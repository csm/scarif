(ns scarif.testing
  "Utilities for using scarif in unit tests."
  (:import [com.netflix.config PolledConfigurationSource PollResult FixedDelayPollingScheduler DynamicConfiguration ConcurrentCompositeConfiguration DynamicURLConfiguration ConfigurationManager]
           [org.apache.commons.configuration SystemConfiguration EnvironmentConfiguration]
           [scarif TriggerableFixedDelayPollingScheduler]))

(defn fn-configuration-source
  "Returns a PolledConfigurationSource that will fetch a map
  by calling a 0-arg function."
  [f]
  (reify PolledConfigurationSource
    (poll [_this _initial _checkpoint]
      (let [result (->> (f)
                        (map (fn [[k v]] [(cond
                                            (string? k)  k
                                            (keyword? k) (.substring (str k) 1)
                                            :else        (str k))
                                          v]))
                        (into {}))]
        (PollResult/createFull result)))))

(defn deref-configuration-source
  "Returns a PolledConfigurationSource that will expect to read
  a map by deref'ing the argument."
  [derefable]
  (fn-configuration-source (fn [] (deref derefable))))

(def config-ref (ref nil))
(def scheduler-ref (ref nil))

(defn init!
  "Initialize Archaius to use a deref'able configuration source.

  derefable should be a deref-able value that returns a map of the new
  configuration (e.g., an atom containing a map).

  Other keyword args:

  poll-frequency -- frequency to poll your configuration source in milliseconds (default 1000).
  url-configuration? -- boolean, to use a DynamicURLConfiguration (true)
  system-configuration? -- boolean, to use a SystemConfiguration (true)
  env-configuration? -- boolean, to use a EnvironmentConfiguration (true)

  Returns the scheduler for the dynamic configuration, which is extended
  with a triggerNow method, to trigger polling early."
  [derefable & {:keys [poll-frequency url-configuration? system-configuration? env-configuration?]
                :or {poll-frequency 1000
                     url-configuration? true
                     system-configuration? true
                     env-configuration? true}}]
  (dosync
    (ref-set config-ref derefable)
    (if-let [sched @scheduler-ref]
      sched
      (let [source (fn-configuration-source (fn [] (deref (deref config-ref))))
            scheduler (TriggerableFixedDelayPollingScheduler. 0 poll-frequency false)
            dyn-config (DynamicConfiguration. source scheduler)
            final-config (ConcurrentCompositeConfiguration.)]
        (.addConfiguration ^ConcurrentCompositeConfiguration final-config dyn-config "deref")
        (when url-configuration?
          (.addConfiguration final-config (DynamicURLConfiguration.) "url"))
        (when system-configuration?
          (.addConfiguration final-config (SystemConfiguration.) "system"))
        (when env-configuration?
          (.addConfiguration final-config (EnvironmentConfiguration.) "env"))
        (if (ConfigurationManager/isConfigurationInstalled)
          (ConfigurationManager/loadPropertiesFromConfiguration final-config)
          (ConfigurationManager/install final-config))
        (ref-set scheduler-ref scheduler)
        scheduler))))