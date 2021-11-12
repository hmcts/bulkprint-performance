package utils

import scala.util.Random

object Common {

  /*======================================================================================
  * Common Utility Functions
  ======================================================================================*/

  /*Function that takes a list of values and a weightings and randomly chooses one based on the weightings

    Example:

    val listOfItemsAndDistributions = Map(1 -> 50.0, 2 -> 30.0, 3 -> 10.0, 4 -> 10.0)
    sample(listOfItemsAndDistributions)

    has a 50% chance of returning 1, 30% chance of returning 2, 10% chance of returning 3, 10% chance of returning 4
    Note: the sum of the distributions doesn't have to total 100
   */
  final def sample[A](distribution: Map[A, Double]): A = {
    val rand = Random.nextDouble * distribution.values.sum
    val counter = distribution.iterator
    var cumulative = 0.0
    while (counter.hasNext) {
      val (item, itemProbability) = counter.next
      cumulative += itemProbability
      if (cumulative >= rand)
        return item
    }
    sys.error(f"Error")
  }

}