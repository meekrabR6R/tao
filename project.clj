(defproject tao-fork "0.1.6"
  :description "Two way data binding for browser history"
  :url "http://github.com/meekrabR6R/tao"
  :author "Nick Miano"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :lein-release {:deploy-via :clojars}

  :jvm-opts ^:replace ["-Xms512m" "-Xmx512m" "-server"]

  :source-paths  ["src"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.10"]
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"]
                 [secretary "1.1.0"]
                 [om "0.5.3"]
                 [sablono "0.2.6"]]

  :plugins [[lein-cljsbuild "1.0.2"]]

  :cljsbuild {
    :builds [{:id "test"
              :source-paths ["src" "test"]
              :compiler {
                :output-to "script/tests.simple.js"
                :output-dir "script/out"
                :source-map "script/tests.simple.js.map"
                :output-wrapper false
                :optimizations :simple}}
             {:id "basic"
              :source-paths ["src" "examples/basic/src"]
              :compiler {
                :output-to "examples/basic/main.js"
                :output-dir "examples/basic/out"
                :source-map true
                :optimizations :none}}
             {:id "om"
              :source-paths ["src" "examples/om/src"]
              :compiler {
                :output-to "examples/om/main.js"
                :output-dir "examples/om/out"
                :source-map true
                :optimizations :none}}]})
