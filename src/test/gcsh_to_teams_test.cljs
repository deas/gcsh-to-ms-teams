(ns gcsh-to-teams-test
  (:require [cljs.test
             :refer (deftest async is testing use-fixtures run-tests)
             ;; :refer-macros [deftest is testing run-tests]
             ]
            [clojure.set :refer [intersection]]
            [gcsh-to-teams :as g :refer (handle-request gcsh-to-teams filter-incidents)]
            ["fs" :as fs]
            ["strftime" :as strftime]
            ;; ["js-time-diff" :as td]
            [goog.string :as gstring]
            goog.string.format))


(defn json-load [path enc]
  (->> (fs/readFileSync path enc)
       (.parse js/JSON)))


(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
  (if (cljs.test/successful? m)
    (println "Success!")
    (println "FAIL")))

(defn logging-test-fixture [f]
  ;; (enable-logging!)
  (f))


;; Logs do no appear in tests?
;; (enable-console-print!)
;; (enable-logging!)

;; TODO: Fixture breaks tests with node?
;; (use-fixtures :once logging-test-fixture)

(deftest incidents-test
  (testing "Map incidents"
    (is (= 1
           (let [incidents-payload (-> (json-load "samples/incidents-test.json" "utf-8")
                                       (js->clj :keywordize-keys true))
                 flt-config {:age (* 1000 3600 24 365)
                             :service {:include #{"Cloud Key Management Service"}}
                             :status {:include #{"SERVICE_DISRUPTION" "SERVICE_OUTAGE"}}
                             :locations {:include #{"europe-west1"}}}
                 filtered-payload (filter-incidents incidents-payload flt-config)
                 mapped-incidents (map g/gcsh-to-teams filtered-payload)]
             (count mapped-incidents))))))

#_(deftest handler-test
  (testing "Handler works"
    (async done
           (done)
           #_(let [js-event (clj->js {:foo "bar"})
                   context nil
                   callback (fn [response]
                              (is (nil? (:error response)) (str response))
                              (done))]
               (handle-request nil nil ;; js-event context callback
                               )))))

(comment
  (+ 1 1)

  ;; (read-string "{:base-url \"http://localhost:8983\", :basic-auth [\"solr\" \"\"]}")
  ;; https://status.cloud.google.com/incidents.json
  ;; (json-load "samples/incidents.json" "utf-8")
  (let [incidents-payload (-> (json-load "samples/incidents-test.json" "utf-8")
                              (js->clj :keywordize-keys true))
        flt-config {:age (* 1000 3600 24 365)
                    :service {:include #{"Cloud Key Management Service"}}
                    :status {:include #{"SERVICE_DISRUPTION" "SERVICE_OUTAGE"}}
                    :locations {:include #{"europe-west1"}}}
        filtered-payload (filter-incidents incidents-payload flt-config)
        mapped-incidents (map g/gcsh-to-teams filtered-payload)]
    (= 1 (count mapped-incidents)))
  #_(js/Date. "2022-03-31T21:17:40+00:00")
  )