package jaicore.search.algorithms.standard.uncertainty.paretosearch;

import jaicore.search.structure.core.Node;
import java.util.Comparator;
import java.lang.Double;

public class CosinusDistanceComparator implements Comparator<Node<?,Double>> {

    public final double x1;
    public final double x2;

    public CosinusDistanceComparator(double x1, double x2) {
        this.x1 = x1;
        this.x2 = x2;
    }

    /**
     * Compares the cosine distance of two nodes to (1,1).
     *
     * @param first
     * @param second
     * @return
     */
    public int compare(Node<?,Double> first, Node<?,Double> second) {

        Double firstF = (Double) first.getAnnotation("f");
        Double firstU = (Double)first.getAnnotation("uncertainty");

        Double secondF = (Double) second.getAnnotation("f");
        Double secondU = (Double)second.getAnnotation("uncertainty");

        double cosDistanceFirst = 1 - this.cosineSimilarity(firstF, firstU);
        double cosDistanceSecond = 1 - this.cosineSimilarity(secondF, secondU);

        return (int)((cosDistanceFirst - cosDistanceSecond) * 10000);
    }

    /**
     * Cosine similarity to x.
     * @param f
     * @param u
     * @return
     */
    public double cosineSimilarity(double f, double u) {
        return (this.x1*f + this.x2*u)/(Math.sqrt(f*f + u*u)*Math.sqrt(this.x1*this.x1 + this.x2*this.x2));
    }

}


