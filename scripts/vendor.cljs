(ns vendor
  "Vendor Squint's browser runtime and rewrite package specifiers to local
   relative imports. This build adapter is temporary until the CL runtime
   takes over the application's domain logic."
  (:require ["node:fs" :as fs]
            ["node:path" :as path]))

(defn copy-file! [source destination]
  (fs/mkdirSync (path/dirname destination) {:recursive true})
  (fs/copyFileSync source destination))

(defn files-under [dir]
  (mapcat (fn [entry]
            (let [p (path/join dir (.-name entry))]
              (if (.isDirectory entry)
                (files-under p)
                [p])))
          (array-seq (fs/readdirSync dir #js {:withFileTypes true}))))

(let [source "node_modules/squint-cljs"
      destination "public/js/squint-cljs"
      runtime (concat ["core.js" "string.js"]
                      (->> (fs/readdirSync (path/join source "src/squint"))
                           (filter #(.endsWith % ".js"))
                           (map #(str "src/squint/" %))))]
  (doseq [file runtime]
    (copy-file! (path/join source file) (path/join destination file)))
  (doseq [file (files-under "public/js")
          :when (or (.endsWith file ".js") (.endsWith file ".mjs"))]
    (let [relative (path/relative (path/dirname file) destination)
          prefix (str (if (or (= "" relative) (not (.startsWith relative ".")))
                        (str "./" relative)
                        relative) "/")
          source-text (fs/readFileSync file "utf8")
          rewritten (-> source-text
                        (.replaceAll "'squint-cljs/" (str "'" prefix))
                        (.replaceAll "\"squint-cljs/" (str "\"" prefix)))]
      (when (not= source-text rewritten)
        (fs/writeFileSync file rewritten))))
  (println "Vendored" (count runtime)
           "Squint runtime modules and rewrote package imports."))