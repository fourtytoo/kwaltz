(ns kwaltz.rainfall)

(defn rainfall [towers]
  (reduce +
          (map -
               (map min
                    (reductions max towers)
                    (reverse (reductions max (reverse towers))))
               towers)))

(comment
  (rainfall [5 3 7 2 6 4 5 9 1 2])
  (rainfall [1 5 3 7 2])
  (rainfall (take 1000000 (repeatedly #(rand-int 10)))))
