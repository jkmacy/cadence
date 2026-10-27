(ns cadence.model-test
  (:require ["node:assert/strict" :as assert]
            [cadence.model :as m]))

(defn activity [options]
  (merge {:id "exercise" :title "Lisp practice" :category "Common Lisp"
          :kind "flexible" :notes "" :repeat "weekly"
          :days [1 3 5] :date nil :start "18:00" :end "18:30"}
         options))

(assert/equal (m/shift-day "2026-10-31" 1) "2026-11-01")
(assert/equal (m/monday-of "2026-11-01") "2026-10-26")
(assert/deepEqual (m/week-days "2026-11-01")
                  ["2026-10-26" "2026-10-27" "2026-10-28" "2026-10-29"
                   "2026-10-30" "2026-10-31" "2026-11-01"])
(assert/equal (m/clock-label "18:30") "6:30 PM")
(assert/equal (m/clock-label "00:00") "12 AM")
(assert/equal (:title (m/clean-item (activity {}))) "Lisp practice")
(assert/ok (try (m/clean-item (activity {:end "17:00"})) false (catch :default _ true)))
(assert/ok (try (m/clean-item (activity {:repeat "weekly" :days []})) false (catch :default _ true)))
(def sample (assoc (m/initial-data) :items [(m/clean-item (activity {}))]))
(assert/equal (count (m/occurrences sample "2026-10-28")) 1)
(assert/equal (count (m/occurrences sample "2026-10-29")) 0)
(def marked (m/status-change sample "exercise" "2026-10-28" :completion))
(assert/equal (:done (first (m/occurrences marked "2026-10-28"))) true)
(assert/equal (:done (first (m/occurrences sample "2026-10-30"))) false)
(assert/equal (:done (first (m/occurrences (m/status-change marked "exercise" "2026-10-28" :skipped) "2026-10-28"))) false)
(assert/equal (count (:items (m/delete-item marked "exercise"))) 0)
(assert/ok (try (m/normalize-data {:version 100 :items []}) false (catch :default _ true)))
(println "Cadence ClojureScript model tests passed.")
