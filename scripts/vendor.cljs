(ns vendor
  (:require ["node:fs" :as fs]
            ["node:path" :as path]))

(defn copy-file! [source destination]
  (fs/mkdirSync (path/dirname destination) {:recursive true})
  (fs/copyFileSync source destination))

(let [source "node_modules/squint-cljs"
      destination "public/js/squint-cljs"
      runtime (concat ["core.js" "string.js"]
                      (->> (fs/readdirSync (path/join source "src/squint"))
                           (filter #(.endsWith % ".js"))
                           (map #(str "src/squint/" %))))]
  (doseq [file runtime]
    (copy-file! (path/join source file) (path/join destination file)))
  (println "Vendored" (count runtime) "Squint runtime modules."))
