(defproject com.github.csm/scarif "0.1.10-SNAPSHOT"
  :description "Dynamic configuration library"
  :url "https://github.com/csm/scarif"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.netflix.archaius/archaius-core "0.7.4"]
                 [com.netflix.archaius/archaius-aws "0.7.4" :exclusions [commons-logging joda-time]]
                 [joda-time "2.10"]]
  :profiles {:devel {:dependencies [[ch.qos.logback/logback-classic "1.1.7"]]
                     :resource-paths ["dev_resources"]}}
  :codox {:output-path "docs"}
  :java-source-paths ["javasrc"]
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["deploy" "clojars"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])