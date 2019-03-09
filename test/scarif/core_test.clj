(ns scarif.core-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer :all]
            [scarif.core :refer :all]
            [scarif.testing :refer :all])
  (:import [com.netflix.config FixedDelayPollingScheduler DynamicConfiguration ConfigurationManager]))

(def dynamic-config (atom {}))

(deftest test-dynamic-vars
  (let [source (deref-configuration-source dynamic-config)
        scheduler (FixedDelayPollingScheduler. 0 10 false)
        config (DynamicConfiguration. source scheduler)]
    (ConfigurationManager/install config))
  (s/def ::test1 keyword?)
  (defconfig test1 :foo)
  (is (= test1 :foo))
  (swap! dynamic-config assoc "scarif.core-test/test1" (pr-str :bar))
  (Thread/sleep 250)  ; ensure propagation
  (is (= test1 :bar))
  (swap! dynamic-config assoc "scarif.core-test/test1" "\"not a keyword\"")
  (Thread/sleep 250)
  (is (= test1 :bar))
  (swap! dynamic-config assoc "scarif.core-test/test1" "::")
  (Thread/sleep 250)
  (is (= test1 :bar))
  (let [changed? (atom nil)]
    (defconfig ^{:on-change (fn [old new] (reset! changed? [old new]))} test2)
    (is (nil? test2))
    (swap! dynamic-config assoc "scarif.core-test/test2" "\"new value\"")
    (Thread/sleep 20)
    (is (= "new value" test2))
    (is (= [nil "new value"] @changed?))))
