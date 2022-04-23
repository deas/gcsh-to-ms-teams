(ns gcsh-to-teams-test
  (:require [cljs.test :refer-macros [deftest async is testing run-tests]]
            [promesa.core :as p]
            [httpurr.client.node :as http]
            [gcsh-to-teams :as g :refer (handle-request gcsh-to-teams base-64-encode filter-incidents)]
            ["fs" :as fs]
            ;; ["strftime" :as strftime]
            ;; [goog.string :as gstring]
            ;; [goog.crypt.base64 :as base64]
            #_goog.string.format))


(defn json-load [path enc]
  (->> (fs/readFileSync path enc)
       (.parse js/JSON)))


(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (if (cljs.test/successful? m)
    (println "Success!")
    (println "FAIL")))

#_(defn logging-test-fixture [f]
  ;; (enable-logging!)
    (f))

;; TODO: Fixture breaks tests with node?
#_(use-fixtures :once logging-test-fixture)

;; TODO: Multiple promises need go/coordiation - for now we leave out teams endpoint
;; https://clojurescript.org/tools/testing
(def flt-config {;; :teams-endpoint "https://localhost:9876"
                 :age (* 1000 3600 24 365)
                 :service {:include #{"Cloud Key Management Service"}}
                 :status {:include #{"SERVICE_DISRUPTION" "SERVICE_OUTAGE"}}
                 :locations {:include #{"europe-west1"}}})

(deftest incidents-test
  (testing "Map incidents"
    (is (= 1
           (let [incidents-payload (-> (json-load "src/test/incidents-test.json" "utf-8")
                                       (js->clj :keywordize-keys true))
                 filtered-payload (filter-incidents incidents-payload flt-config)
                 mapped-incidents (map g/gcsh-to-teams filtered-payload)]
             (count mapped-incidents))))))

#_(deftest handler-test
  (testing "Handler works"
    (async done
           (let [data (clj->js {:data (-> flt-config pr-str base-64-encode)})
                 context nil
                 callback (fn [response]
                            (is (true? (and (map? response)
                                            (not (:error response))))
                                (str "response appears to be an error " response))
                            (done))]
             (with-redefs [http/get (fn [url req opts]
                                      (p/resolved {:body (fs/readFileSync "src/test/incidents-test.json" "utf-8")
                                                   :status 200}))
                           http/post (fn [url req opts]
                                       (p/resolved {:body "{}" :status 200}))]
               (handle-request data context callback))))))

(comment
  (+ 1 1)
  ;; (read-string "{:base-url \"http://localhost:8983\", :basic-auth [\"solr\" \"\"]}")
  ;; https://status.cloud.google.com/incidents.json
  ;; (json-load "src/test/incidents.json" "utf-8")
  (let [incidents-payload (-> (json-load "src/test/incidents-test.json" "utf-8")
                              (js->clj :keywordize-keys true))
        flt-config {:age (* 1000 3600 24 365)
                    :service {:include #{"Cloud Key Management Service"}}
                    :status {:include #{"SERVICE_DISRUPTION" "SERVICE_OUTAGE"}}
                    :locations {:include #{"europe-west1"}}}
        filtered-payload (filter-incidents incidents-payload flt-config)
        mapped-incidents (map gcsh-to-teams filtered-payload)]
    (= 1 (count mapped-incidents)))
  #_(js/Date. "2022-03-31T21:17:40+00:00")
  )