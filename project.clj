(defproject clojars-rss "2021.01.28"
  :description "Unofficially generated RSS feed for libraries recently registered on Clojars"
  :url "https://github.com/athos/clojars-rss"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.2"]
                 [cheshire "5.10.0"]
                 [org.martinklepsch/clj-http-lite "0.4.3"]
                 [pogonos "0.1.1"]]
  :main clojars-rss.main
  :repl-options {:init-ns clojars-rss.main}
  :profiles {:uberjar {:aot :all}
             :dev {:plugins [[lein-shell "0.5.0"]]}}
  :aliases
  {"native"
   ["shell"
    "native-image" "--report-unsupported-elements-at-runtime"
    "--initialize-at-build-time" "--no-server"
    "-H:Name=./${:name}"
    "-H:EnableURLProtocols=https"
    "-H:IncludeResources=feed_template.mustache"
    "-J-Dclojure.spec.skip-macros=true"
    "-J-Dclojure.compiler.direct-linking=true"]})
