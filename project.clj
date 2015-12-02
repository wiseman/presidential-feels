(defproject com.lemonodor/presidential-feels "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.climate/claypoole "1.0.0"]
                 [edu.stanford.nlp/stanford-corenlp "3.5.2"]
                 [edu.stanford.nlp/stanford-corenlp "3.5.2"
                  :classifier "models"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.3.1"]]
  :main ^:skip-aot com.lemonodor.presidential-feels
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
