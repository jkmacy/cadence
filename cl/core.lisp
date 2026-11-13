;;;; Cadence's platform-independent domain model.
;;;; ANSI Common Lisp; no network, UI, database, or implementation extensions.
(defpackage #:cadence.core
  (:use #:cl)
  (:export #:parse-date #:weekday #:minute-of-day #:occurs-on-p
           #:daily-entries #:overlaps-p #:day-summary #:open-intervals))
(in-package #:cadence.core)

(defun parse-date (text)
  "Return three values YEAR, MONTH, DAY for a valid YYYY-MM-DD (1900+).
Return NIL for an invalid civil date. Uses UTC for deterministic checking."
  (when (and (stringp text) (= 10 (length text))
             (char= #\- (char text 4)) (char= #\- (char text 7))
             (loop for i in '(0 1 2 3 5 6 8 9)
                   always (digit-char-p (char text i))))
    (let ((year (parse-integer text :start 0 :end 4))
          (month (parse-integer text :start 5 :end 7))
          (day (parse-integer text :start 8 :end 10)))
      (when (and (<= 1900 year 9999) (<= 1 month 12) (<= 1 day 31))
        (handler-case
            (multiple-value-bind (second minute hour dd mm yy)
                (decode-universal-time
                 (encode-universal-time 0 0 12 day month year 0) 0)
              (declare (ignore second minute hour))
              (when (and (= dd day) (= mm month) (= yy year))
                (values year month day)))
          (error () nil))))))

(defun weekday (date)
  "Return Sunday=0 through Saturday=6, matching Cadence's JSON format."
  (multiple-value-bind (y m d) (parse-date date)
    (when y
      (mod (1+ (nth-value 6
                (decode-universal-time
                 (encode-universal-time 0 0 12 d m y 0) 0)))
           7))))

(defun minute-of-day (text)
  "Return a number in [0,1440), or NIL. Empty values are unscheduled."
  (when (and (stringp text) (= 5 (length text))
             (char= #\: (char text 2))
             (every #'digit-char-p (concatenate 'string
                                                  (subseq text 0 2)
                                                  (subseq text 3 5))))
    (let ((hour (parse-integer text :end 2))
          (minute (parse-integer text :start 3)))
      (when (and (< hour 24) (< minute 60))
        (+ (* 60 hour) minute)))))

(defun occurs-on-p (item date)
  "ITEM is a property list with :REPEAT, :DATE, :DAYS, :START, etc.
Repeating weekdays are integers Sunday=0 ... Saturday=6."
  (and (parse-date date)
       (if (equal (getf item :repeat) "weekly")
           (member (weekday date) (getf item :days) :test #'eql)
           (equal date (getf item :date)))))

(defun daily-entries (items date)
  "Return an ordered, new list of entries scheduled on DATE."
  (stable-sort (remove-if-not (lambda (item) (occurs-on-p item date))
                              (copy-list items))
               #'<
               :key (lambda (item)
                      (or (minute-of-day (getf item :start)) 9999))))

(defun overlaps-p (first second)
  "Only entries with two valid, nonempty intervals can overlap."
  (let ((a (minute-of-day (getf first :start)))
        (b (minute-of-day (getf first :end)))
        (c (minute-of-day (getf second :start)))
        (d (minute-of-day (getf second :end))))
    (and a b c d (< a b) (< c d) (< a d) (< c b))))

(defun day-summary (items date)
  "Summarize scheduled local entries; external calendars are not included."
  (let ((entries (daily-entries items date)))
    (list :total (length entries)
          :fixed (count "fixed" entries :key (lambda (e) (getf e :kind))
                        :test #'equal)
          :flexible (count "flexible" entries
                           :key (lambda (e) (getf e :kind))
                           :test #'equal))))

(defun open-intervals (items &key (from 0) (to 1440))
  "Return free (START END) minute pairs after subtracting fixed events.
Clips to FROM/TO, merges collisions, does not assume imported calendars
or untimed obligations have been included."
  (unless (and (integerp from) (integerp to) (<= 0 from to 1440))
    (error "Invalid day window"))
  (let* ((busy
           (remove nil
             (mapcar
               (lambda (item)
                 (let ((start (minute-of-day (getf item :start)))
                       (end (minute-of-day (getf item :end))))
                   (when (and (equal "fixed" (getf item :kind))
                              start end (< start end))
                     (let ((lo (max from start))
                           (hi (min to end)))
                       (when (< lo hi) (list lo hi))))))
               items)))
         (ordered (sort busy #'< :key #'first))
         (cursor from)
         (free nil))
    (dolist (block ordered)
      (when (< cursor (first block))
        (push (list cursor (first block)) free))
      (setf cursor (max cursor (second block))))
    (when (< cursor to)
      (push (list cursor to) free))
    (nreverse free)))
