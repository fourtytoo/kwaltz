(ns kwaltz.numbers
  (:require [clojure.string :as string]))

;; Very simplistic algorithm to convert numeral words to numbers.  It
;; should work with cardinals and ordinals alike.

(def number-word-table
  {;; English:
   "zero" 0
   "one" 1
   "first" 1
   "two" 2
   "second" 2
   "three" 3
   "third" 3
   #"four(th)?" 4
   "five" 5
   "fifth" 5
   #"six(th)?" 6
   #"seven(th)?" 7
   #"eight(th)?" 8
   "nine" 9
   "ninth" 9
   #"ten(th)?" 10
   #"eleven(th)?" 11
   "twelve" 12
   "twelfth" 12
   #"thirteen(th)?" 13
   #"fourteen(th)?" 14
   #"fifteen(th)?" 15
   #"sixteen(th)?" 16
   #"seventeen(th)?" 17
   #"eighteen(th)?" 18
   #"nineteen(th)?" 19
   #"twenty|(ieth)" 20
   #"thirty|(ieth)" 30
   #"fourty|(ieth)" 40
   #"fifty|(ieth)" 50
   #"sixty|(ieth)" 60
   #"seventy|(ieth)" 70
   #"eighty|(ieth)" 80
   #"ninety|(ieth)" 90

   ;; German:
   ;; because of their ending we match first 13-90
   #"dreizehn.*" 13
   #"vierzehn.*" 14
   #"fünfzehn.*" 15
   #"sechzehn.*" 16
   #"siebzehn.*" 17
   #"achtzehn.*" 18
   #"neunzehn.*" 19
   #"zwanzig.*" 20
   #"dreißig.*" 30
   #"vierzig.*" 40
   #"fünfzig.*" 50
   #"sechzig.*" 60
   #"siebzig.*" 70
   #"achtzig.*" 80
   #"neunzig.*" 90
   "eins" 1
   #"erst.*" 1
   #"zwei.*" 2
   "drei" 3
   #"dritte.*" 3
   #"vier.*" 4
   #"fünf.*" 5
   #"sechs.*" 6
   #"sieben.*" 7
   #"acht.*" 8
   #"neun.*" 9
   #"zehn.*" 10
   #"elf.*" 11
   #"zwölf.*" 12
   })

(defn word->number [word]
  (->> number-word-table
       (filter (fn [[k v]]
                 (if (string? k)
                   (= k word)
                   (re-matches k word))))
       first
       second))

(defn sum-words
  "Return a pair: the sum and the number of words that were summed
  together."
  [words]
  (let [numbers (->> words
                     (map string/lower-case)
                     (map word->number)
                     (take-while (comp not nil?)))]
    [(reduce + numbers) (count numbers)]))

#_(sum-words ["Zweite" "Kapitel"])
#_(sum-words (string/split "four t" #"\s+"))
#_(sum-words (string/split "foo two" #"\s+"))
