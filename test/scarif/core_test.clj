(ns scarif.core-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer :all]
            [scarif.core :refer :all]
            [scarif.testing :refer :all])
  (:import [com.netflix.config FixedDelayPollingScheduler DynamicConfiguration ConfigurationManager]))

(def dynamic-config (atom {}))

(deftest test-dynamic-vars
  (let [scheduler (init! dynamic-config)]
    (s/def ::test1 keyword?)
    (defconfig test1 :foo)
    (is (= test1 :foo))
    (swap! dynamic-config assoc "scarif.core-test/test1" (pr-str :bar))
    (Thread/sleep 1001)  ; ensure propagation
    (is (= test1 :bar))
    (swap! dynamic-config assoc "scarif.core-test/test1" (pr-str :baz))
    (.triggerNow scheduler)
    (is (= test1 :baz))
    (swap! dynamic-config assoc "scarif.core-test/test1" "\"not a keyword\"")
    (.triggerNow scheduler)
    (is (= test1 :baz))
    (swap! dynamic-config assoc "scarif.core-test/test1" "::")
    (.triggerNow scheduler)
    (is (= test1 :baz))
    (let [changed? (atom nil)]
      (defconfig ^{:on-change (fn [old new] (reset! changed? [old new]))} test2)
      (is (nil? test2))
      (swap! dynamic-config assoc "scarif.core-test/test2" "\"new value\"")
      (.triggerNow scheduler)
      (is (= "new value" test2))
      (is (= [nil "new value"] @changed?)))))
