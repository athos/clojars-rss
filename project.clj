(defproject clojars-rss "0.1.0-SNAPSHOT"
  :description "Unofficially generated RSS feed for libraries recently registered on Clojars"
  :url "https://github.com/athos/clojars-rss"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [cheshire "5.10.0"]
                 [clj-http "3.10.1"]
                 [pogonos "0.1.0-SNAPSHOT"]]
  :repl-options {:init-ns clojars-rss.core})
