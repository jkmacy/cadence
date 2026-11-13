(load (merge-pathnames "../core.lisp"
                        (or *load-truename* *default-pathname-defaults*)))
(in-package #:cl-user)

(defun check-equal (expected actual label)
  (unless (equal expected actual)
    (error "~A: expected ~S but found ~S" label expected actual)))

(check-equal '(2026 11 13)
             (multiple-value-list (cadence.core:parse-date "2026-11-13"))
             "Civil date")
(check-equal nil (cadence.core:parse-date "2026-02-30") "Bad date")
(check-equal nil (cadence.core:parse-date "2026-1-03") "Non-ISO date")
(check-equal 5 (cadence.core:weekday "2026-11-13") "Friday")
(check-equal 0 (cadence.core:weekday "2026-11-15") "Sunday")
(check-equal 510 (cadence.core:minute-of-day "08:30") "Morning time")
(check-equal 0 (cadence.core:minute-of-day "00:00") "Midnight")
(check-equal nil (cadence.core:minute-of-day "24:00") "Invalid hour")
(check-equal nil (cadence.core:minute-of-day "") "Untimed event")

(let* ((work '(:id "work" :title "Work" :kind "fixed"
               :repeat "weekly" :days (1 2 3 4 5)
               :start "08:00" :end "12:00"))
       (study '(:id "study" :title "Lisp" :kind "flexible"
                :repeat "once" :date "2026-11-13"
                :start "13:00" :end "14:00"))
       (other '(:id "other" :title "Church" :kind "fixed"
                :repeat "once" :date "2026-11-13"
                :start "11:45" :end "12:30"))
       (items (list study work other))
       (today (cadence.core:daily-entries items "2026-11-13")))
  (check-equal '("work" "other" "study")
               (mapcar (lambda (e) (getf e :id)) today)
               "Daily ordering")
  (check-equal nil (cadence.core:occurs-on-p work "2026-11-15")
               "Recurring weekdays")
  (check-equal t (cadence.core:overlaps-p work other) "Overlap")
  (check-equal nil (cadence.core:overlaps-p work study) "Disjoint")
  (check-equal '(:total 3 :fixed 2 :flexible 1)
               (cadence.core:day-summary items "2026-11-13") "Summary")
  (check-equal '((420 480) (750 900))
               (cadence.core:open-intervals today :from 420 :to 900)
               "Free time merges overlapping fixed commitments"))
(format t "Cadence ANSI Common Lisp core: all checks passed.~%")
