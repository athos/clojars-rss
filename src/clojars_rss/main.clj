(ns clojars-rss.main
  (:gen-class)
  (:require [clj-http.client :as http]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [pogonos.core :as pg]
            [pogonos.output :as output])
  (:import [java.text SimpleDateFormat]
           [java.util Date Locale TimeZone]))

(defn- fetch-libs
  ([since]
   (fetch-libs since (System/currentTimeMillis)))
  ([since until]
   (let [query (format "at:[%d TO %d]" since until)
         res (http/get "https://clojars.org/search"
                       {:as :json
                        :query-params {"q" query "format" "json"}})]
     (->> (get-in res [:body :results])
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
        link (str "https://clojars.org/" name)]
    {:guid (str link "/versions/" (:version lib))
     :title name
     :link link
     :description (:description lib)
     :publish-date (->> (:created lib)
                        Date.
                        format-date)}))

(defn- generate-feed [libs build-date output-file]
  (let [items (map ->feed-item libs)]
    (pg/render-resource "feed_template.mustache"
                        {:items items :last-build-date (format-date build-date)}
                        {:output (output/to-file output-file)})))

(defn- distinct-by [f]
  (fn [rf]
    (let [seen (volatile! #{})]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (let [k (f input)]
           (if (contains? @seen k)
             result
             (do (vswap! seen conj k)
                 (rf result input)))))))))

(defn -main [libs-file feed-file]
  (let [old-libs (with-open [r (io/reader libs-file)]
                   (mapv edn/read-string (line-seq r)))
        libs (->> (fetch-libs (:created (first old-libs)))
                  (sort-by :created (comparator >)))
        libs' (->> (concat libs old-libs)
                   (into [] (comp (distinct-by (juxt :group_name :jar_name :version))
                                  (take 256))))]
    (dump-libs libs' libs-file)
    (generate-feed libs' (Date.) feed-file)))
