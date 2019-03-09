(ns scarif.testing
  "Utilities for using scarif in unit tests."
  (:import [com.netflix.config PolledConfigurationSource PollResult FixedDelayPollingScheduler DynamicConfiguration ConcurrentCompositeConfiguration DynamicURLConfiguration ConfigurationManager]
           [org.apache.commons.configuration SystemConfiguration EnvironmentConfiguration]))

(defn deref-configuration-source
  "Returns a PolledConfigurationSource that will expect to read
  a map by deref'ing the argument."
  [derefable]
  (reify PolledConfigurationSource
    (poll [_this _initial _checkpoint]
      (let [result (->> @derefable
                        (map (fn [[k v]] [(cond
                                            (string? k)  k
                                            (keyword? k) (.substring (str k) 1)
                                            :else        (str k))
                                          v]))
                        (into {}))]
        (PollResult/createFull result)))))

(defn init!
  "Initialize Archaius to use a deref'able configuration source.

  derefable should be a deref-able value that returns a map of the new
  configuration (e.g., an atom containing a map).

  Other keyword args:

  poll-frequency -- frequency to poll your configuration source in milliseconds (default 1000).
  url-configuration? -- boolean, to use a DynamicURLConfiguration (true)
  system-configuration? -- boolean, to use a SystemConfiguration (true)
  env-configuration? -- boolean, to use a EnvironmentConfiguration (true)"
  [derefable & {:keys [poll-frequency url-configuration? system-configuration? env-configuration?]
                :or {poll-frequency 1000
                     url-configuration? true
                     system-configuration? true
                     env-configuration? true}}]
  (let [source (deref-configuration-source derefable)
        scheduler (FixedDelayPollingScheduler. 0 poll-frequency false)
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
      (ConfigurationManager/install final-config))))