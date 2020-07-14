(ns clojars-rss.main
  (:gen-class)
  (:require [cheshire.core :as json]
            [clj-http.lite.client :as http]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [pogonos.core :as pg]
            [pogonos.output :as output])
  (:import [java.text SimpleDateFormat]
           [java.util Date Locale TimeZone]))

(defn- fetch-libs
  ([since]
   (fetch-libs since (System/currentTimeMillis)))
  ([since until]
   (let [query (format "at:[%d TO %d]" since until)
         {:keys [body]} (http/get "https://clojars.org/search"
                                  {:query-params {"q" query "format" "json"}})]
     (->> (json/parse-string body keyword)
          :results
          (map (fn [lib] (update lib :created #(Long/parseLong %))))))))

(defn- dump-libs [libs dump-file]
  (with-open [w (io/writer dump-file)]
    (binding [*out* w]
      (doseq [lib libs]
        (prn lib)))))

(def ^:private format-date
  (let [date-format (doto (SimpleDateFormat. "EEE, d MMM yyyy HH:mm:ss Z" Locale/US)
                      (.setTimeZone (TimeZone/getTimeZone "UTC")))]
    (fn [^Date d]
      (.format date-format d))))

(defn- ->feed-item [lib]
  (let [name (cond->> (:jar_name lib)
               (not= (:group_name lib) (:jar_name lib))
               (str (:group_name lib) \/))
        link (format "https://clojars.org/%s/versions/%s" name (:version lib))]
    {:guid link
     :title (format "[%s \"%s\"]" name (:version lib))
     :link link
     :version (:version lib)
     :description (:description lib)
     :publish-date (->> (long (:created lib))
                        Date.
                        format-date)}))

(defn- generate-feed [libs build-date output-file]
  (let [items (map ->feed-item libs)]
    (pg/render-resource "feed_template.mustache"
                        {:items items :build-date (format-date build-date)}
                        {:output (output/to-file output-file)})))

(defn- distinct-by
  ([f] (distinct-by f (constantly true)))
  ([f tolerant?]
   (fn [rf]
     (let [seen (volatile! {})]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (let [k (f input)]
            (if (some-> (get @seen k) peek (tolerant? input))
              result
              (do (vswap! seen update k (fnil conj []) input)
                  (rf result input))))))))))

(def lib-coord (juxt :group_name :jar_name :version))

(defn- updated-shortly? [lib1 lib2]
  (< (Math/abs (- (long (:created lib2)) (long (:created lib1)))) 30000))

(defn- update-libs [xf old-libs new-libs]
  (->> (concat old-libs new-libs)
       ;; keep older one if same lib occurs twice in a short range
       (into () (distinct-by lib-coord updated-shortly?))
       ;; keep newest one if same lib occurs more than once
       (into () (comp (distinct-by lib-coord) xf (take 256)))))

(defn- generate-with [xf libs-file output-dir]
  (let [old-libs (with-open [r (io/reader libs-file)]
                   (mapv edn/read-string (line-seq r)))
        new-libs (->> (fetch-libs (:created (last old-libs)))
                      (sort-by :created))
        libs (update-libs xf old-libs new-libs)
        output-file (as-> (io/file libs-file) <>
                      (.getName <>)
                      (str/replace <> #"\.edn$" ".xml")
                      (io/file output-dir <>))]
    (dump-libs libs libs-file)
    (generate-feed (reverse libs) (Date.) output-file)))

(defn -main [latest-libs-file stable-libs-file output-dir]
  (generate-with identity latest-libs-file output-dir)
  (generate-with (remove #(re-matches #".*-SNAPSHOT$" (:version %)))
                 stable-libs-file output-dir))
