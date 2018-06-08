(ns scarif.dynamodb
  (:import [com.amazonaws.services.dynamodbv2 AmazonDynamoDBClient]
           [com.netflix.config.sources DynamoDbConfigurationSource]
           [com.netflix.config FixedDelayPollingScheduler DynamicConfiguration ConfigurationManager ConcurrentCompositeConfiguration DynamicURLConfiguration]
           [com.amazonaws.regions Region Regions]
           [org.apache.commons.configuration SystemConfiguration EnvironmentConfiguration]))

(defn init!
  "Initialize Archaius for dynamodb polling.

  Optional keys:

  :region -- The AWS region to connect to. Defaults to us-west-2.
  :poll-frequency -- The frequency to poll dynamodb for config changes, in milliseconds. Defaults to 60000.
  :url-configuration? -- Whether to install a DynamicURLConfiguration in the config chain (true).
  :system-configuration? -- Whether to install a SystemConfiguration in the config chain (true).
  :env-configuration? -- Whether to install a EnvironmentConfiguration in the config chain (true)."
  [& {:keys [region poll-frequency url-configuration? system-configuration? env-configuration?]
      :or {region "us-west-2"
           poll-frequency 60000
           url-configuration? true
           system-configuration? true
           env-configuration? true}}]
  (let [ddb (doto (AmazonDynamoDBClient.)
              (.setRegion (Region/getRegion (Regions/fromName region))))
        source (DynamoDbConfigurationSource. ddb)
        scheduler (FixedDelayPollingScheduler. 0 poll-frequency false)
        dynConfig (DynamicConfiguration. source scheduler)
        finalConfig (ConcurrentCompositeConfiguration.)]
    (.addConfiguration ^ConcurrentCompositeConfiguration finalConfig dynConfig "dynamodb")
    (when url-configuration?
      (.addConfiguration finalConfig (DynamicURLConfiguration.) "url"))
    (when system-configuration?
      (.addConfiguration finalConfig (SystemConfiguration.) "system"))
    (when env-configuration?
      (.addConfiguration finalConfig (EnvironmentConfiguration.) "env"))
    (if (ConfigurationManager/isConfigurationInstalled)
      (ConfigurationManager/loadPropertiesFromConfiguration finalConfig)
      (ConfigurationManager/install finalConfig))))