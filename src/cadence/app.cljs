(ns cadence.app
  "Cadence's browser UI. All scheduling logic lives in cadence.model."
  (:require [cadence.model :as model]))

(def storage-key "cadence-local-private-v1")
(def weekdays ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Sat"])
(defn by-id [id] (.getElementById js/document id))
(defn set-text! [id value] (set! (.-textContent (by-id id)) (str value)))
(defn set-value! [id value] (set! (.-value (by-id id)) (str value)))
(defn listen! [node event callback] (.addEventListener node event callback))
(defn load-data []
  (try
    (if-let [saved (.getItem js/localStorage storage-key)]
      (model/normalize-data (js/JSON.parse saved))
      (model/initial-data))
    (catch :default error
      (js/console.error "Cannot read local schedule; do not overwrite old data" error)
      nil)))

(defonce state (atom {:data (load-data)
                      :selected (model/local-date)
                      :view :day
                      :screen :today
                      :editing nil}))

(defn toast! [message]
  (let [node (by-id "toast")]
    (set! (.-textContent node) message)
    (.add (.-classList node) "show")
    (js/setTimeout #(.remove (.-classList node) "show") 4000)))

(declare render! open-editor! set-data! set-screen!)

(defn set-data! [new-data]
  (try
    (.setItem js/localStorage storage-key (js/JSON.stringify new-data))
    (swap! state assoc :data new-data)
    (render!)
    true
    (catch :default error
      (js/console.error "Cannot save schedule" error)
      (toast! "Couldn't save data. Browser storage may be unavailable or full.")
      false)))

(defn element
  ([tag] (element tag "" ""))
  ([tag class-name text]
   (let [node (.createElement js/document tag)]
     (set! (.-className node) class-name)
     (set! (.-textContent node) (str text))
     node)))

(defn append! [node & children]
  (doseq [child children]
    (when child (.append node child)))
  node)

(defn button [label class-name on-click]
  (let [b (element "button" class-name label)]
    (set! (.-type b) "button")
    (listen! b "click" on-click)
    b))

(defn date-label [day short?]
  (let [opts (if short?
               #js {:weekday "short" :day "numeric"}
               #js {:weekday "long" :month "long" :day "numeric"})
        fmt (js/Intl.DateTimeFormat. "en-US" opts)]
    (.format fmt (js/Date. (str day "T12:00:00")))))

(defn go-day! [day]
  (swap! state assoc :selected day :view :day :screen :calendar)
  (render!))

(defn update-status! [item status]
  (let [{:keys [selected data]} @state]
    (set-data! (model/status-change data (:id item) (or (:on item) selected) status))))

(defn activity-card [item others]
  (let [conflict? (some (fn [other]
                          (and (not= (:id other) (:id item))
                               (not (:skipped other))
                               (not (:skipped item))
                               (model/overlaps? item other))) others)
        card (element "article"
                      (str "activity-card"
                           (when (= "flexible" (:kind item)) " flexible")
                           (when (:done item) " done-card")
                           (when (:skipped item) " skipped-card")
                           (when conflict? " is-overlap")) "")
        bar (element "div" "activity-bar" "")
        main (element "div" "activity-main" "")
        meta (element "div" "activity-meta" "")
        category (element "span" "category-pill" (:category item))
        name (element "h4" "" (:title item))
        actions (element "div" "activity-actions" "")]
    (.setAttribute bar "aria-hidden" "true")
    (append! meta category (element "span" "" (model/time-label item)))
    (when (= "weekly" (:repeat item)) (append! meta (element "span" "" "↻ Weekly")))
    (append! main meta name)
    (when-not (empty? (:notes item))
      (append! main (element "p" "" (:notes item))))
    (when conflict?
      (append! main (element "span" "activity-warning" "Overlaps another activity")))
    (when (:skipped item)
      (append! main (element "span" "activity-warning" "Skipped today")))
    (append! actions
             (button (if (:done item) "✓ Done" "Mark done")
                     (str "mini-button" (when (:done item) " is-active"))
                     #(update-status! item :completion))
             (button (if (:skipped item) "Undo skip" "Skip today")
                     (str "mini-button secondary" (when (:skipped item) " is-active"))
                     #(update-status! item :skipped))
             (button "Edit" "mini-button secondary" #(open-editor! item)))
    (append! card bar main actions)))

(defn activity-section [title items others]
  (let [wrap (element "div")
        heading (element "div" "section-head" "")
        cards (element "div" "activity-list" "")]
    (append! heading
             (element "h3" "" title)
             (element "span" "" (str (count items) (if (= 1 (count items)) " activity" " activities"))))
    (doseq [item items] (append! cards (activity-card item others)))
    (append! wrap heading cards)))

(defn render-day! []
  (let [{:keys [data selected]} @state
        root (by-id "dayBlocks")
        entries (model/occurrences data selected)]
    (.replaceChildren root)
    (if (empty? entries)
      (let [empty-state (element "div" "empty-state" "")]
        (append! empty-state
                 (element "h3" "" "A day with room to breathe.")
                 (element "p" "" "Add a commitment or flexible activity, or leave the day open.")
                 (button "Add your first activity" "secondary-button" #(open-editor! nil)))
        (append! root empty-state))
      (append! root
               (activity-section "Fixed commitments" (filter #(= "fixed" (:kind %)) entries) entries)
               (activity-section "Flexible activities" (filter #(= "flexible" (:kind %)) entries) entries)))))

(defn render-week! []
  (let [{:keys [data selected]} @state
        root (by-id "weekGrid")]
    (.replaceChildren root)
    (doseq [day (model/week-days selected)]
      (let [entries (model/occurrences data day)
            stats (model/day-summary data day)
            column (element "div" (str "week-column" (when (= day selected) " current")) "")
            head (button (date-label day true) "week-head" #(go-day! day))]
        (append! head (element "small" "" (str (:done stats) "/" (:total stats) " done")))
        (append! column head)
        (when (empty? entries)
          (append! column (element "p" "week-empty" "Open day")))
        (doseq [item entries]
          (append! column
                   (append! (element "div"
                                     (str "week-item" (when (= "flexible" (:kind item)) " flex")
                                          (when (:done item) " complete")
                                          (when (:skipped item) " ignored")) "")
                            (element "span" "" (:title item))
                            (element "span" "time" (model/time-label item)))))
        (append! root column)))))

(defn render-strip! []
  (let [{:keys [selected data]} @state
        root (by-id "weekStrip")]
    (.replaceChildren root)
    (doseq [day (model/week-days selected)]
      (let [active? (= day selected)
            count-items (count (model/occurrences data day))
            pill (button ""
                         (str "day-pill" (when active? " active")
                              (when (= day (model/local-date)) " today"))
                         #(go-day! day))
            dots (element "span" "dotrow" "")]
        (.setAttribute pill "aria-label" (date-label day false))
        (.setAttribute pill "aria-current" (if active? "date" "false"))
        (doseq [_ (range (min count-items 3))]
          (append! dots (element "span" "" "")))
        (append! pill
                 (element "span" "dow" (get weekdays (model/weekday day)))
                 (element "span" "day-num" (js/Number (.slice day -2)))
                 dots)
        (append! root pill)))))


(def screens [:today :calendar :plan :apps])

(defn set-screen! [screen]
  (when (some #{screen} screens)
    (swap! state assoc :screen screen)
    (render!)))

(defn render-home! []
  (let [today (model/local-date)
        data (:data @state)
        entries (model/occurrences data today)
        root (by-id "todayBlocks")
        current-time (+ (* 60 (.getHours (js/Date.)))
                        (.getMinutes (js/Date.)))
        upcoming (first (filter
                         #(let [start (model/minutes (:start %))
                                end (model/minutes (:end %))]
                            (and (not (:skipped %))
                                 (not (:done %))
                                 start
                                 (>= (or end start) current-time)))
                         entries))]
    (set-text! "heroDate" (date-label today false))
    (set-text! "heroSummary"
               (let [stats (model/day-summary data today)]
                 (str (:done stats) " of " (:total stats) " activities complete")))
    (set-text! "nowTitle" (if upcoming (:title upcoming) "Your time is your own"))
    (set-text! "nowMeta" (if upcoming
                          (str (model/time-label upcoming) " · " (:category upcoming))
                          "No more timed commitments are scheduled for today."))
    (.replaceChildren root)
    (if (empty? entries)
      (let [blank (element "div" "empty-state" "")]
        (append! blank
                 (element "h3" "" "A little room to breathe.")
                 (element "p" "" "Capture something to do, or keep the day open.")
                 (button "Add an activity" "secondary-button" #(open-editor! nil)))
        (append! root blank))
      (append! root
               (activity-section "Fixed commitments"
                                 (filter #(= "fixed" (:kind %)) entries) entries)
               (activity-section "Flexible activities"
                                 (filter #(= "flexible" (:kind %)) entries) entries)))))

(defn render-plan! []
  (let [data (:data @state)
        days (model/week-days (model/local-date))
        root (by-id "planBlocks")
        total (reduce + (map #(count (model/occurrences data %)) days))]
    (set-text! "planSummary"
               (str total " planned " (if (= total 1) "activity" "activities")))
    (.replaceChildren root)
    (doseq [day days]
      (let [entries (model/occurrences data day)
            panel (element "section" "plan-day" "")
            heading (button (date-label day true) "plan-date" #(go-day! day))
            details (element "div" "plan-details" "")]
        (if (empty? entries)
          (append! details (element "p" "quiet-text" "Open day"))
          (doseq [entry entries]
            (append! details
                     (element "p" ""
                              (str (model/time-label entry)
                                   " · " (:title entry)
                                   (when (:done entry) " ✓"))))))
        (append! panel heading details)
        (append! root panel)))))

(defn quick-capture! [event]
  (.preventDefault event)
  (let [field (by-id "quickTitle")
        title (.trim (.-value field))]
    (when (not (empty? title))
      (try
        (let [data (:data @state)
              entry (model/clean-item
                     {:id (model/new-id) :title title :category "Other"
                      :kind "flexible" :notes "" :repeat "once" :days []
                      :date (model/local-date) :start "" :end ""})]
          (when (set-data! (assoc data :items (conj (:items data) entry)))
            (set! (.-value field) "")
            (toast! "Added to today")))
        (catch :default error
          (js/console.error error)
          (toast! "Could not add activity"))))))

(defn render! []
  (let [{:keys [selected view data]} @state]
    (when data
      (let [stats (model/day-summary data selected)
            day? (= :day view)
            date-head (if day?
                        (date-label selected false)
                        (str "Week of " (date-label (model/monday-of selected) true)))]
        (set-value! "datePicker" selected)
        (doseq [screen screens]
          (set! (.-hidden (by-id (str "screen-" (name screen))))
                (not= screen (:screen @state))))
        (doseq [control (js/Array.from (.querySelectorAll js/document "[data-screen]"))]
          (let [active (= (name (:screen @state)) (.getAttribute control "data-screen"))]
            (.toggle (.-classList control) "active" active)
            (if active
              (.setAttribute control "aria-current" "page")
              (.removeAttribute control "aria-current"))))
        (render-home!)
        (render-plan!)
        (set-text! "periodLabel" (if day? "DAILY OVERVIEW" "WEEK AT A GLANCE"))
        (set-text! "dateHeading" date-head)
        (.toggle (.-classList (by-id "dayTab")) "active" day?)
        (.toggle (.-classList (by-id "weekTab")) "active" (not day?))
        (.setAttribute (by-id "dayTab") "aria-pressed" (str day?))
        (.setAttribute (by-id "weekTab") "aria-pressed" (str (not day?)))
        (set! (.-hidden (by-id "dayView")) (not day?))
        (set! (.-hidden (by-id "weekView")) day?)
        (render-strip!)
        (if day? (render-day!) (render-week!))))))

(defn form-field [form name]
  (.namedItem (.-elements form) name))

(defn field-value [form name]
  (.-value (form-field form name)))

(defn checked-repeat [form]
  (let [selected (.querySelector form "input[name='repeat']:checked")]
    (.-value selected)))

(defn repeat-panels! []
  (let [weekly (= "weekly" (checked-repeat (by-id "activityForm")))]
    (set! (.-hidden (by-id "oncePanel")) weekly)
    (set! (.-hidden (by-id "weeklyPanel")) (not weekly))
    (set! (.-required (form-field (by-id "activityForm") "date")) (not weekly))))

(defn open-editor! [entry]
  (swap! state assoc :editing (:id entry))
  (let [form (by-id "activityForm")
        day (:selected @state)
        data (:data @state)
        days (or (:days entry) [(model/weekday day)])
        chosen (or (:repeat entry) "once")]
    (.reset form)
    (set! (.-hidden (by-id "activityError")) true)
    (set-text! "activityTitle" (if entry "Edit activity" "Add an activity"))
    (set! (.-hidden (by-id "deleteActivity")) (nil? entry))
    (doseq [[key value] [["title" (or (:title entry) "")]
                         ["category" (or (:category entry) "")]
                         ["kind" (or (:kind entry) "flexible")]
                         ["start" (or (:start entry) "")]
                         ["end" (or (:end entry) "")]
                         ["date" (or (:date entry) day)]
                         ["notes" (or (:notes entry) "")]]]
      (set! (.-value (form-field form key)) value))
    (doseq [option (js/Array.from (.querySelectorAll form "input[name='repeat']"))]
      (set! (.-checked option) (= chosen (.-value option))))
    (doseq [checkbox (js/Array.from (.querySelectorAll form "input[name='days']"))]
      (set! (.-checked checkbox) (some #{(js/Number (.-value checkbox))} days)))
    (let [suggestions (by-id "categorySuggestions")]
      (.replaceChildren suggestions)
      (doseq [name (get-in data [:settings :categories])]
        (let [node (element "option")]
          (.setAttribute node "value" name)
          (append! suggestions node))))
    (repeat-panels!)
    (.showModal (by-id "activityDialog"))
    (.focus (form-field form "title"))))

(defn submit-activity! [event]
  (.preventDefault event)
  (let [form (by-id "activityForm")
        id (:editing @state)
        raw {:id (or id (model/new-id))
             :title (field-value form "title")
             :category (field-value form "category")
             :kind (field-value form "kind")
             :repeat (checked-repeat form)
             :start (field-value form "start")
             :end (field-value form "end")
             :date (field-value form "date")
             :notes (field-value form "notes")
             :days (mapv #(js/Number (.-value %))
                         (filter #(.-checked %)
                                 (js/Array.from (.querySelectorAll form "input[name='days']"))))}]
    (try
      (let [entry (model/clean-item raw)
            data (:data @state)
            items (if id
                    (mapv #(if (= (:id %) id) entry %) (:items data))
                    (conj (:items data) entry))
            next-data (assoc data :items items)
            cats (get-in data [:settings :categories])
            next-data (if (some #{(:category entry)} cats)
                        next-data
                        (assoc next-data :settings
                               (assoc (:settings data) :categories (conj cats (:category entry)))))]
        (when (set-data! next-data)
          (.close (by-id "activityDialog"))
          (toast! (if id "Activity updated" "Activity added"))))
      (catch :default error
        (set-text! "activityError" (.-message error))
        (set! (.-hidden (by-id "activityError")) false)))))

(defn erase-entry! []
  (let [id (:editing @state)
        entry (first (filter #(= id (:id %)) (:items (:data @state))))]
    (when (and entry (js/confirm (str "Delete " (:title entry) " and its saved completion history?")))
      (when (set-data! (model/delete-item (:data @state) id))
        (.close (by-id "activityDialog"))
        (toast! "Activity deleted")))))

(defn open-settings! []
  (set-value! "wakeInput" (get-in (:data @state) [:settings :wake]))
  (set-value! "bedInput" (get-in (:data @state) [:settings :bedtime]))
  (.showModal (by-id "settingsDialog")))

(defn save-settings! []
  (let [wake (.-value (by-id "wakeInput"))
        bed (.-value (by-id "bedInput"))]
    (if (and (model/minutes wake) (model/minutes bed))
      (let [data (:data @state)]
        (when (set-data! (assoc data :settings (assoc (:settings data) :wake wake :bedtime bed)))
          (toast! "Sleep targets saved")))
      (toast! "Choose valid wake and bedtime targets"))))

(defn download-json! [filename value]
  (let [blob (js/Blob. #js [(str (js/JSON.stringify value nil 2) "\n")]
                       #js {:type "application/json"})
        url (.createObjectURL js/URL blob)
        link (element "a")]
    (set! (.-href link) url)
    (set! (.-download link) filename)
    (append! (.-body js/document) link)
    (.click link)
    (.remove link)
    (js/setTimeout #(.revokeObjectURL js/URL url) 10000)))

(defn b64 [bytes]
  (loop [i 0 result ""]
    (if (< i (.-length bytes))
      (let [end (min (+ i 8192) (.-length bytes))
            letters (loop [j i buffer []]
                      (if (< j end)
                        (recur (inc j) (conj buffer (js/String.fromCharCode (aget bytes j))))
                        buffer))]
        (recur end (str result (.join letters ""))))
      (js/btoa result))))

(defn from-b64 [encoded]
  (let [decoded (js/atob encoded)
        bytes (js/Uint8Array. (.-length decoded))]
    (doseq [i (range (.-length decoded))]
      (aset bytes i (.charCodeAt decoded i)))
    bytes))

(defn ^:async encryption-key [pass salt action]
  (let [subtle (.-subtle js/crypto)
        material (await (.importKey subtle "raw" (.encode (js/TextEncoder.) pass)
                                    "PBKDF2" false #js ["deriveKey"]))]
    (await (.deriveKey subtle
                       #js {:name "PBKDF2" :salt salt :iterations 310000 :hash "SHA-256"}
                       material
                       #js {:name "AES-GCM" :length 256}
                       false #js [action]))))

(defn ^:async export-encrypted! []
  (if-not (.-subtle js/crypto)
    (toast! "Encrypted backups require HTTPS")
    (let [pass (js/prompt "Choose a backup passphrase (at least 12 characters):")]
      (when pass
        (if (< (count pass) 12)
          (toast! "Use at least 12 characters")
          (when (= pass (js/prompt "Confirm the passphrase:"))
            (try
              (let [salt (.getRandomValues js/crypto (js/Uint8Array. 16))
                    iv (.getRandomValues js/crypto (js/Uint8Array. 12))
                    key (await (encryption-key pass salt "encrypt"))
                    bytes (.encode (js/TextEncoder.) (js/JSON.stringify (:data @state)))
                    ciphertext (js/Uint8Array. (await (.encrypt (.-subtle js/crypto)
                                                             #js {:name "AES-GCM" :iv iv} key bytes)))]
                (download-json! (str "cadence-encrypted-" (model/local-date) ".json")
                                {:type "cadence-encrypted-backup" :version 1
                                 :kdf "PBKDF2-SHA256" :iterations 310000
                                 :cipher "AES-256-GCM" :salt (b64 salt)
                                 :iv (b64 iv) :ciphertext (b64 ciphertext)})
                (toast! "Encrypted backup downloaded. Keep the passphrase safe."))
              (catch :default error
                (js/console.error error)
                (toast! "Backup failed")))))))))

(defn ^:async import-file! [file]
  (when file
    (try
      (when (> (.-size file) (* 3 1024 1024))
        (throw (js/Error. "Backup is too large")))
      (let [raw (js/JSON.parse (await (.text file)))
            data (if (= "cadence-encrypted-backup" (:type raw))
                   (do
                     (when-not (and (= 1 (:version raw)) (= 310000 (:iterations raw))
                                    (= "PBKDF2-SHA256" (:kdf raw)) (= "AES-256-GCM" (:cipher raw)))
                       (throw (js/Error. "Unsupported encryption format")))
                     (let [pass (js/prompt "Enter your backup passphrase:")]
                       (when-not pass (throw (js/Error. "No passphrase entered")))
                       (let [salt (from-b64 (:salt raw))
                             iv (from-b64 (:iv raw))
                             encrypted (from-b64 (:ciphertext raw))]
                         (when-not (and (= 16 (.-length salt)) (= 12 (.-length iv)))
                           (throw (js/Error. "Invalid salt or IV")))
                         (let [key (await (encryption-key pass salt "decrypt"))
                               plain (await (.decrypt (.-subtle js/crypto)
                                                      #js {:name "AES-GCM" :iv iv} key encrypted))]
                           (js/JSON.parse (.decode (js/TextDecoder.) plain))))))
                   raw)
            imported (model/normalize-data data)]
        (when (js/confirm (str "Import " (count (:items imported))
                               " activities? This REPLACES this device's current schedule."
                               " Download a backup first if necessary."))
          (when (set-data! imported)
            (.close (by-id "settingsDialog"))
            (toast! "Schedule imported on this device"))))
      (catch :default error
        (js/console.error error)
        (toast! "Import failed. Check the file and passphrase."))
      (finally (set! (.-value (by-id "importFile")) "")))))

(defn ^:async copy-week! []
  (try
    (await (.writeText (.-clipboard js/navigator)
                       (model/week-text (:data @state) (:selected @state))))
    (toast! "Week summary copied. Share only when you choose.")
    (catch :default error
      (toast! "Clipboard unavailable. Use a supported HTTPS browser."))))

(defn wire! []
  (doseq [control (js/Array.from (.querySelectorAll js/document "[data-screen]"))]
    (listen! control "click"
             #(set-screen! (keyword (.getAttribute control "data-screen")))))
  (listen! (by-id "jumpCalendar") "click" #(set-screen! :calendar))
  (listen! (by-id "detailedAdd") "click" #(open-editor! nil))
  (listen! (by-id "planAdd") "click" #(do (swap! state assoc :selected (model/local-date))
                                           (open-editor! nil)))
  (listen! (by-id "quickForm") "submit" quick-capture!)
  (listen! (by-id "todayButton") "click" #(go-day! (model/local-date)))
  (listen! (by-id "prevButton") "click"
           #(do (swap! state update :selected model/shift-day
                       (if (= :week (:view @state)) -7 -1))
                (render!)))
  (listen! (by-id "nextButton") "click"
           #(do (swap! state update :selected model/shift-day
                       (if (= :week (:view @state)) 7 1))
                (render!)))
  (listen! (by-id "datePicker") "change"
           #(when-not (empty? (.-value (.-target %)))
              (go-day! (.-value (.-target %)))))
  (listen! (by-id "dayTab") "click" #(do (swap! state assoc :view :day) (render!)))
  (listen! (by-id "weekTab") "click" #(do (swap! state assoc :view :week) (render!)))
  (listen! (by-id "addButton") "click" #(open-editor! nil))
  (listen! (by-id "settingsButton") "click" open-settings!)
  (doseq [node (js/Array.from (.querySelectorAll js/document "[data-close]"))]
    (listen! node "click" #(.close (by-id (.getAttribute node "data-close")))))
  (doseq [node (js/Array.from (.querySelectorAll js/document "input[name='repeat']"))]
    (listen! node "change" repeat-panels!))
  (listen! (by-id "activityForm") "submit" submit-activity!)
  (listen! (by-id "deleteActivity") "click" erase-entry!)
  (listen! (by-id "saveSettings") "click" save-settings!)
  (listen! (by-id "exportReadable") "click"
           #(when (js/confirm "Unencrypted JSON reveals your schedule. Download anyway?")
              (download-json! (str "cadence-readable-" (model/local-date) ".json")
                              (:data @state))))
  (listen! (by-id "exportEncrypted") "click" export-encrypted!)
  (listen! (by-id "importButton") "click" #(.click (by-id "importFile")))
  (listen! (by-id "importFile") "change"
           #(import-file! (aget (.. % -target -files) 0)))
  (listen! (by-id "copyWeek") "click" copy-week!)
  (listen! (by-id "clearButton") "click"
           #(when (and (js/confirm "Erase the private schedule on this device?")
                       (js/confirm "Final confirmation: delete all activities and completion history?"))
              (when (set-data! (model/initial-data))
                (.close (by-id "settingsDialog"))
                (toast! "Schedule erased"))))
  (when (and (exists? js/navigator.serviceWorker)
             (.startsWith (.-protocol js/location) "http"))
    (.catch (.register (.-serviceWorker js/navigator) "./sw.js")
            #(js/console.warn "Service worker unavailable" %)))
  (if (:data @state)
    (render!)
    (let [root (by-id "dayBlocks")]
      (set! (.-textContent root)
            "Existing private schedule could not be read. It has NOT been overwritten. Export it from the browser before clearing site data."))))

(wire!)
