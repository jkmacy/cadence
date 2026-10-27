(ns cadence.model
  "Pure scheduling, recurrence, validation and summary logic.
   No DOM, storage, calendar service or network code belongs here.")

(def version 1)
(def categories ["Work" "Church" "School" "Common Lisp" "Writing"
                 "Prayer & Scripture" "Rest & leisure" "Other"])

(defn initial-data []
  {:version version
   :settings {:wake "05:30" :bedtime "20:30" :categories categories}
   :items [] :completion {} :skipped {}})

(defn new-id []
  (if (and (exists? js/crypto) (.-randomUUID js/crypto))
    (.randomUUID js/crypto)
    (str "entry-" (.now js/Date) "-" (rand-int 10000000))))

(defn pad [n]
  (.padStart (str n) 2 "0"))

(defn local-date
  ([] (local-date (js/Date.)))
  ([d] (str (.getFullYear d) "-" (pad (inc (.getMonth d))) "-" (pad (.getDate d)))))

(defn parse-day [day]
  (when (and (string? day) (re-matches #"\d{4}-\d{2}-\d{2}" day))
    (let [[year month number] (map #(js/Number %) (.split day "-"))
          d (js/Date. year (dec month) number 12)]
      (when (and (= year (.getFullYear d))
                 (= month (inc (.getMonth d)))
                 (= number (.getDate d)))
        d))))

(defn shift-day [day n]
  (let [d (parse-day day)]
    (when-not d (throw (js/Error. "Invalid date")))
    (.setDate d (+ (.getDate d) n))
    (local-date d)))

(defn weekday [day]
  (when-let [d (parse-day day)] (.getDay d)))

(defn monday-of [day]
  (shift-day day (- (mod (+ (weekday day) 6) 7))))

(defn week-days [day]
  (let [m (monday-of day)]
    (mapv #(shift-day m %) (range 7))))

(defn minutes [clock]
  (when (and (string? clock) (re-matches #"(?:[01]\d|2[0-3]):[0-5]\d" clock))
    (let [[hour minute] (map #(js/Number %) (.split clock ":"))]
      (+ (* 60 hour) minute))))

(defn clock-label [clock]
  (if-let [n (minutes clock)]
    (let [hour (quot n 60) minute (mod n 60)]
      (str (let [h (mod hour 12)] (if (zero? h) 12 h))
           (if (zero? minute) "" (str ":" (pad minute)))
           (if (< hour 12) " AM" " PM")))
    "Anytime"))

(defn time-label [item]
  (let [start (:start item) end (:end item)]
    (if (empty? (or start ""))
      "Anytime"
      (if (empty? (or end ""))
        (clock-label start)
        (str (clock-label start) "–" (clock-label end))))))

(defn clean-item [item]
  (let [title (.trim (str (or (:title item) "")))
        category (.trim (str (or (:category item) "")))
        category (if (empty? category) "Other" category)
        notes (or (:notes item) "")
        kind (if (= "fixed" (:kind item)) "fixed" "flexible")
        repeat (if (= "weekly" (:repeat item)) "weekly" "once")
        days (->> (or (:days item) [])
                  (filter #(and (integer? %) (<= 0 % 6)))
                  distinct sort vec)
        date (:date item)
        start (or (:start item) "")
        end (or (:end item) "")]
    (when (or (empty? title) (> (count title) 120))
      (throw (js/Error. "Activity title must be 1–120 characters.")))
    (when (> (count category) 50)
      (throw (js/Error. "Category must not exceed 50 characters.")))
    (when (> (count notes) 2000)
      (throw (js/Error. "Notes must not exceed 2000 characters.")))
    (when (and (= repeat "weekly") (empty? days))
      (throw (js/Error. "Choose at least one repeating weekday.")))
    (when (and (= repeat "once") (not (parse-day date)))
      (throw (js/Error. "Choose a valid date.")))
    (when (and (not= start "") (nil? (minutes start)))
      (throw (js/Error. "Invalid start time.")))
    (when (and (not= end "") (nil? (minutes end)))
      (throw (js/Error. "Invalid end time.")))
    (when (and (not= end "") (= start ""))
      (throw (js/Error. "End time requires a start time.")))
    (when (and (not= end "") (<= (minutes end) (minutes start)))
      (throw (js/Error. "End time must be later than start time. Split overnight activities.")))
    {:id (if (and (string? (:id item))
                  (re-matches #"[A-Za-z0-9_-]{1,90}" (:id item)))
           (:id item) (new-id))
     :title title :category category :notes notes :kind kind :repeat repeat
     :days (if (= repeat "weekly") days [])
     :date (when (= repeat "once") date)
     :start start :end end}))

(defn normalize-data [raw]
  (when (or (not= version (:version raw))
            (not (array? (:items raw)))
            (> (count (:items raw)) 1000))
    (throw (js/Error. "Unsupported or invalid Cadence backup.")))
  (let [items (mapv clean-item (:items raw))
        ids (mapv :id items)
        known (set ids)
        settings (:settings raw)
        time-setting (fn [k fallback]
                       (if (minutes (get settings k)) (get settings k) fallback))
        preserved-categories (->> (or (:categories settings) categories)
                                  (filter #(and (string? %) (<= 1 (count %) 50)))
                                  distinct (take 60) vec)
        valid-status (fn [statuses]
                       (into {} (take 5000
                         (filter (fn [[key flag]]
                                   (let [[id date] (.split key "@")]
                                     (and (= true flag) (contains? known id) (parse-day date))))
                                 (js/Object.entries (or statuses {}))))))]
    (when (not= (count ids) (count known))
      (throw (js/Error. "Duplicate activity IDs in backup.")))
    {:version version
     :settings {:wake (time-setting :wake "05:30")
                :bedtime (time-setting :bedtime "20:30")
                :categories preserved-categories}
     :items items
     :completion (valid-status (:completion raw))
     :skipped (valid-status (:skipped raw))}))

(defn occurrences [data day]
  (let [dow (weekday day)]
    (->> (:items data)
         (filter #(if (= "weekly" (:repeat %))
                    (some #{dow} (:days %))
                    (= day (:date %))))
         (map (fn [entry]
                (let [key (str (:id entry) "@" day)]
                  (assoc entry :on day
                         :done (true? (get (:completion data) key))
                         :skipped (true? (get (:skipped data) key))))))
         (sort-by #(vector (or (minutes (:start %)) 9999) (:title %)))
         vec)))

(defn overlaps? [a b]
  (let [a-start (minutes (:start a)) a-end (minutes (:end a))
        b-start (minutes (:start b)) b-end (minutes (:end b))]
    (and a-start a-end b-start b-end (< a-start b-end) (< b-start a-end))))

(defn day-summary [data day]
  (let [active (remove :skipped (occurrences data day))]
    {:total (count active)
     :done (count (filter :done active))
     :fixed (count (filter #(= "fixed" (:kind %)) active))
     :flexible (count (filter #(= "flexible" (:kind %)) active))}))

(defn status-change [data item-id day status]
  (let [key (str item-id "@" day)
        other (if (= status :completion) :skipped :completion)
        current (get data status)]
    (-> data
        (assoc status (if (get current key) (dissoc current key) (assoc current key true)))
        (assoc other (dissoc (get data other) key)))))

(defn delete-item [data item-id]
  (let [keep-key (fn [[key _]] (not (.startsWith key (str item-id "@"))))]
    (-> data
        (assoc :items (vec (remove #(= item-id (:id %)) (:items data))))
        (assoc :completion (into {} (filter keep-key (:completion data))))
        (assoc :skipped (into {} (filter keep-key (:skipped data)))))))

(defn week-text [data selected]
  (str "Cadence — week of " (monday-of selected) "\n"
       (apply str (map (fn [day]
                         (let [entries (occurrences data day)]
                           (str day "\n"
                                (if (empty? entries) "  Open day\n"
                                    (apply str (map (fn [entry]
                                                      (str "  " (time-label entry) " — " (:title entry)
                                                           " (" (:category entry) ")"
                                                           (when (:done entry) " [done]")
                                                           (when (:skipped entry) " [skipped]")
                                                           "\n")) entries)))
                                "\n")))
                       (week-days selected)))))
