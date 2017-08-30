(ns kwaltz.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [kwaltz.core-test]
   [kwaltz.common-test]))

(enable-console-print!)

(doo-tests 'kwaltz.core-test
           'kwaltz.common-test)
