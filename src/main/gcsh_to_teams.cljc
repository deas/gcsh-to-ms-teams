(ns gcsh-to-teams
  (:require [taoensso.timbre :as timbre
             :refer [infof warn warnf]]
            [clojure.set :refer [intersection]]
            [clojure.string :refer [upper-case join]]
            #?(:cljs [httpurr.client.node :as http])
            [httpurr.status :as s]
            [promesa.core :as p]
            #?(:cljs [cljs.reader :refer [read-string]])
            #?(:cljs ["strftime" :as strftime])
            #?(:cljs [goog.crypt.base64 :as base64])))

(defn start [] (pr "Dev Started"))

(defn reload [] (pr "Dev Reloaded"))

(defn to-json [clj]
  #?(:cljs (.stringify js/JSON (clj->js clj))))

(defn json-parse [str]
  #?(:cljs (.parse js/JSON str)))

(defn to-clj [json]
  #?(:cljs (js->clj json :keywordize-keys true)))

(defn to-date-string [millis]
  #?(:cljs (strftime "%B %d, %Y %H:%M:%S" (js/Date. millis))))

(defn get-env [key]
  #?(:clj (System/getenv key)
     :cljs (aget js/process "env" key)))

(defn date
  ([v]
   #?(:cljs (js/Date. v)))
  ([]
   #?(:cljs (js/Date.))))

(timbre/merge-config!
 {:output-fn
  (fn [data]
    (let [{:keys [level ?err #_vargs msg_ ?ns-str ?file hostname_
                  timestamp_ ?line]} data
          output-data (cond->
                       {:timestamp (force timestamp_)
                        :host (force hostname_)
                        :severity (upper-case (name level))
                        :message (force msg_)}
                        (or ?ns-str ?file) (assoc :ns (or ?ns-str ?file))
                        ?line (assoc :line ?line)
                        ?err (assoc :err (timbre/stacktrace ?err {:stacktrace-fonts {}})))]
      (to-json output-data)))})


(defn filter-incidents
  [incidents-payload flt-config]
  (let [ref-date (date (- (-> (date) .getTime) (:age flt-config)))
        loc-include (-> flt-config :locations :include)
        loc-filter #(or (not (seq loc-include))
                        (seq (intersection loc-include
                                            ;; currently_affected_locations may be empty
                                           (into #{} (:currently_affected_locations %)))))
        srv-include (-> flt-config :service :include)
        srv-filter #(or (not (seq srv-include))
                        (contains? srv-include
                                   (:service_name %)))
        status-include (-> flt-config :status :include)
        status-filter #(or (not (seq status-include))
                           (contains? status-include
                                      (-> % :most_recent_update :status)))
        date-filter #(< ref-date (-> % :most_recent_update
                                     :created
                                     (js/Date.)
                                     .getTime))
        all-filter #(and (date-filter %) (status-filter %) (loc-filter %) (srv-filter %))]
    (->> incidents-payload
         (filter all-filter)
         ;; (filter date-filter)
         ;; (filter status-filter)
         ;; (filter loc-filter)
         ;; (filter srv-filter)
         )))

(defn gcsh-to-teams
  "https://docs.microsoft.com/de-de/graph/api/resources/chatmessage?view=graph-rest-1.0
  https://docs.microsoft.com/de-de/outlook/actionable-messages/message-card-reference"
  [payload]
  (let [incident (merge (select-keys payload [:service_name :external_desc :number])
                        (select-keys (:most_recent_update payload) [:status :text :created :affected_locations]))
        created (.getTime (date (:created incident))) #_(* 1000 (:started_at incident))
        facts [{:title "Service" :value (:service_name incident)}
               {:title "Description" :value (:external_desc incident)}
               {:title "Created" :value (to-date-string created)}
               {:title "Status" :value (:status incident)}
               {:title "Locations" :value (join "," (map :id (:affected_locations incident)))}]
        body [{:type "FactSet"
               :facts facts}
              {:type "TextBlock" :text (:text incident) :color "attention" #_(if (= "open" state) "attention" "default")}]]
    (infof "Creating teams message for incident %s" (:number incident))
    {:type "message"
     :importance "urgent" #_(if (= "open" state) "urgent" "normal")
     :attachments [{:contentType "application/vnd.microsoft.card.adaptive"
                    :contentUrl nil
                    :content {:$schema "http://adaptivecards.io/schemas/adaptive-card.json"
                              :type "AdaptiveCard"
                              :version "1.2"
                              :body body}}]}))

(defn base-64-decode [str]
  ;; #?(:cljs base64/decodeString str)
  (.from js/Buffer str "base64"))

(defn base-64-encode [str]
  (let [buff (.from js/Buffer str "utf-8")]
    (.toString buff "base64")))

(defn unwrap-burrito [burrito]
  (-> burrito
      to-clj
      :data
      base-64-decode
      .toString
      read-string))

(defn handle
  [flt-config success-fn fail-fn]
  (infof "Handle with config %s" (cond-> flt-config
                                   (:teams-endpoint flt-config) (assoc :teams-endpoint "***redacted***")))
  (let [teams-endpoint (:teams-endpoint flt-config)
        incidents-endpoint "https://status.cloud.google.com/incidents.json"
        opts {:timeout 2000}
        google-req {}
        next-fn (if teams-endpoint
                  (fn [raw-payload]
                    (let [unfiltered-payload (->> raw-payload
                                                  :body
                                                  .toString
                                                  (.parse js/JSON)
                                                  to-clj)
                          filtered-payload (filter-incidents unfiltered-payload flt-config)]
                      (infof "Got %d incidents, %d filtered" (count unfiltered-payload) (count filtered-payload))
                      (if (> (count filtered-payload) 0)
                        (p/all (mapv #(let [body (-> (gcsh-to-teams %) to-json)]
                                       (http/post teams-endpoint
                                                  {:headers {"Content-Type" "application/json"
                                                ;; TODO: charset -> use buffer
                                                             "Content-Length" (count body)}
                                                   :body body}
                                                  opts))
                                     filtered-payload))
                        (p/resolved "No incidents passed the filter/sent to teams"))))
                  #(p/resolved "No teams endpoint configured"))]
    ;; (infof "Got data %s, context %s" unwrapped-burrito (to-clj context))
    (infof "Getting all incidents from %s" incidents-endpoint)
    (-> (http/get incidents-endpoint google-req opts)
        (p/then #(if (s/success? %)
                   (next-fn %)
                   (p/rejected %)))
        (p/then success-fn)
        (p/catch fail-fn))))

(defn handle-request
  [data context callback]
  (handle (merge {:teams-endpoint (get-env "TEAMS_WEBHOOK_URL")}
                 (unwrap-burrito data))
          (fn [r]
            (infof "Received : %s" r)
            (callback))
          (fn [r]
            (warnf "Received : %s" r)
            (let [msg "Payload invalid"]
              (warn msg)
              (callback (js/Error. msg))))))

(defn handle-request-http
  "https://expressjs.com/en/api.html"
  [req res]
  (handle nil ;; flt-config
               (fn [r]
                 (infof "Received : %s" r)
                 (doto res
                   (.status (or (:status r) s/ok))
                   (.send "👍")))
          (fn [r]
            (warnf "Received : %s" r)
            (doto res
              (.status s/internal-server-error)
              (.send "Invalid request")))))


(comment
  ;; (#((println "a")(+ 1 2)))

  ;; s/success?
  ;; (.stringify js/JSON #js {:a 1})
  (.. js/process -env -HOME)
  ;; (def s (atom nil))

  (let [_ (def s (atom nil))

        fail-fn #(reset! s (or (:body %) (.-message %)))
        success-fn #(reset! s % #_(.-message %))
        a-fn (fn [success-fn fail-fn]
               (let [url  "https://status.cloud.google.com/incidents.json"
                     opts {:timeout 2000}
                     req {;; :headers {"Content-Type" "application/json"}
                         ;; :body "{}"
                          }]
                 (-> (http/get url req opts)
                     (p/then #(if (s/success? %)
                                (http/get "https://www.google.com")
                                (p/rejected %)))
                     (p/then success-fn)
                     (p/catch fail-fn))))]
    (a-fn success-fn fail-fn))
  s/internal-server-error

  @s)