package uk.ac.susx.tag.classificationframework.classifiers;

import it.unimi.dsi.fastutil.ints.*;
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction;
import org.apache.commons.math3.analysis.solvers.NewtonRaphsonSolver;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import uk.ac.susx.tag.classificationframework.datastructures.ProcessedInstance;
import uk.ac.susx.tag.classificationframework.datastructures.FeatureMarginalsConstraint;
import uk.ac.susx.tag.classificationframework.Util;

/**
 * Created by thomas on 2/22/14.
 *
 * Feature Marginals method based on Lucas, Downey (2013): http://aclweb.org/anthology/P/P13/P13-1034.pdf
 *
 * Variable names (more or less) correspond to names used in paper.
 */
public class NaiveBayesClassifierFeatureMarginals extends NaiveBayesClassifier {

    // Turning the meta-knobs, max number of iterations
    public static final int DEFAULT_MAX_EVALUATIONS_NEWTON_RAPHSON = 1000000;

	private int posLabel;
    private int otherLabel;

    // Maximum Number of iterations in the optimisation process
    private int maxEvaluationsNewtonRaphson;

    // Map for optimal class-conditional probabilities per label
    private Int2ObjectMap<Int2DoubleOpenHashMap> optClassCondFMProbs = new Int2ObjectOpenHashMap<>();

    // TODO: Might it be a good idea to have NaiveBayesFMPreComputed?

    /**
     * Create a source of NaiveBayesClassifierFeatureMarginals and specify the class labels.
	 * The current implementation of Feature Marginals is inherently binary, so there must not
	 * be more than 2 class labels. For multiclass problems the NaiveBayesClassifierFeatureMarginals
	 * needs to be wrapped in a NaiveBayesOVRClassifier.
	 *
	 * For efficiency reasons it needs to know about the labels in advance.
     */
	public NaiveBayesClassifierFeatureMarginals(IntSet labels)
	{
		super(labels);
		this.maxEvaluationsNewtonRaphson = DEFAULT_MAX_EVALUATIONS_NEWTON_RAPHSON;

		this.initLabels();
	}

	public NaiveBayesClassifierFeatureMarginals(IntSet labels, int maxEvaluationsNewtonRaphson)
	{
		super(labels);
		this.maxEvaluationsNewtonRaphson = maxEvaluationsNewtonRaphson;

		this.initLabels();
	}

    public void setMaxEvaluationsNewtonRaphson(int maxEvaluationsNewtonRaphson)
    {
        this.maxEvaluationsNewtonRaphson = maxEvaluationsNewtonRaphson;
    }
    public int getMaxEvaluationsNewtonRaphson()
    {
        return this.maxEvaluationsNewtonRaphson;
    }

    /**
     * For the final version override the train method, first create NB/NB EM model as usual,
     * then calculate the Feature Marginals
     *
     * @param unlabelledData
     * @param labelledData
     */
    public void calculateFeatureMarginals(Iterable<ProcessedInstance> labelledData, Iterable<ProcessedInstance> unlabelledData) {
        // P(w) for all words
        Int2DoubleOpenHashMap wordProb = Util.calculateWordProbabilities(unlabelledData);

        // Calculate P(t|+) & P(t|-); the probabilities of a randomly drawn token from the labelled set being positive (ie. from a positively labelled instance) or negative
        // Calculate N(+), N(w|+), N(!w|+), N(-), N(w|-), N(!w|-)
        double posTokenProb = 0.;
        double negTokenProb = 0.;
        int tokenCount = 0;
        int posTokenCount = 0;
        int negTokenCount = 0;

        Int2IntOpenHashMap posWordMap = new Int2IntOpenHashMap();
        posWordMap.defaultReturnValue(0);

        Int2IntOpenHashMap negWordMap = new Int2IntOpenHashMap();
        negWordMap.defaultReturnValue(0);

        for (ProcessedInstance i : labelledData) {
            // collecting P(t|+), N(+)
            posTokenCount += (i.getLabel() == this.posLabel) ? i.features.length : 0;
            tokenCount += i.features.length;

            // N(w|+), N(w|-)
            if (i.getLabel() == this.posLabel) {
                for (int featIdx : i.features) {
                    posWordMap.addTo(featIdx, 1);
                }
            } else {
                for (int featIdx : i.features) {
                    negWordMap.addTo(featIdx, 1);
                }
            }
        }

        // N(-)
        negTokenCount = tokenCount - posTokenCount;

        // P(t|+), P(t|-)
        posTokenProb = ((double) posTokenCount) / tokenCount;
        negTokenProb = 1. - posTokenProb;

        //-- Shorthands K, l --//

        // l
        double l = posTokenProb / negTokenProb;

        // K
        Int2DoubleOpenHashMap kMap = new Int2DoubleOpenHashMap();
        kMap.defaultReturnValue(0);

        for (int key : wordProb.keySet()) {
            kMap.put(key, (wordProb.get(key) / negTokenProb));
        }

        // Target Interval [0, P(w) / P(t|+)]
        Int2DoubleOpenHashMap targetIntervalMax = new Int2DoubleOpenHashMap();
        for (int k : wordProb.keySet()) {
            targetIntervalMax.put(k, (wordProb.get(k) / posTokenProb));
        }

        // go for the real shit
        UnivariateDifferentiableFunction featureMarginals = null;
        NewtonRaphsonSolver solver = new NewtonRaphsonSolver();
        double result = -1.;

        Int2DoubleOpenHashMap pWPosFMOptimisedMap = new Int2DoubleOpenHashMap();
        pWPosFMOptimisedMap.defaultReturnValue(-1.);

        Int2DoubleOpenHashMap pWNegFMOptimisedMap = new Int2DoubleOpenHashMap();
        pWNegFMOptimisedMap.defaultReturnValue(-1.);

        for (int key : wordProb.keySet()) {
            // N(w|+), N(!w|+), N(w|-), N(!w|-), k
            int nWPos = posWordMap.get(key);
            int nNotWPos = posTokenCount - nWPos;
            int nWNeg = negWordMap.get(key);
            double k = kMap.get(key);
            int nNotWNeg = negTokenCount - nWNeg;

            // Check for N(!w|+) > 0 and N(w|-) > 0
            if (nNotWPos > 0 && nWNeg > 0) {

                featureMarginals = new FeatureMarginalsConstraint(nWPos, nNotWPos, nWNeg, nNotWNeg, k, l);

                try {
                    result = solver.solve(this.maxEvaluationsNewtonRaphson, featureMarginals, 0, targetIntervalMax.get(key));
                } catch (TooManyEvaluationsException ex) {
                    ex.printStackTrace();
                }
            }

            // Check result in target interval [0 P(w) / P(t|+)]
            if (result > 0. && result <= targetIntervalMax.get(key)) {
                pWPosFMOptimisedMap.put(key, result);

                // Solve for P(w|-)
                double pWNegOpt = (wordProb.get(key) - (result * posTokenProb)) / negTokenProb;
                pWNegFMOptimisedMap.put(key, pWNegOpt);
            }
            result = -1.;
        }

        // Normalise Probabilities
        pWPosFMOptimisedMap = this.normaliseProbabilities(pWPosFMOptimisedMap);
        pWNegFMOptimisedMap = this.normaliseProbabilities(pWNegFMOptimisedMap);

        this.optClassCondFMProbs.put(this.posLabel, pWPosFMOptimisedMap);
        this.optClassCondFMProbs.put(this.otherLabel, pWNegFMOptimisedMap);
    }

    /**
     * Train on labelled documents.
     * See class documentation for training examples.
     * @param labelledDocs Iterable over TrainingInstances (which are (label, feature-list) pairs.
     * @param unlabelledDocs Iterable over unlabelled Instances, used for Feature Marginals
     * @param weight The weighting applied to the counts obtained.
     */
    public void train(Iterable<ProcessedInstance> labelledDocs, Iterable<ProcessedInstance>unlabelledDocs, double weight)
    {
        // Train on labelled data
        super.train(labelledDocs, weight);

        // Calculate Feature Marginals over unlabelled data
        this.calculateFeatureMarginals(labelledDocs, unlabelledDocs);
    }

    public void train(Iterable<ProcessedInstance> labelledDocs, Iterable<ProcessedInstance> unlabelledDocs)
    {
        this.train(labelledDocs, unlabelledDocs, 1.);
    }

    @Override
    public Int2DoubleOpenHashMap logpriorPlusLoglikelihood(int[] features)
    {
        Int2DoubleOpenHashMap labelScores = new Int2DoubleOpenHashMap();
        Int2DoubleMap labelPriors = labelPriors();
        Int2DoubleOpenHashMap fmMap = null;
        for (int label : this.labels) {
            double loglikelihood = 0.0;
            for (int feature : features) {
                if (this.vocab.contains(feature)){
                    if (super.getFromMap(label, this.optClassCondFMProbs).containsKey(feature)) {
                        loglikelihood += Math.log(super.getFromMap(label, this.optClassCondFMProbs).get(feature));
                    } else {
                        loglikelihood += Math.log(super.likelihood(feature, label));
                    }
                }
            }
            labelScores.put(label, Math.log(labelPriors.get(label)) + loglikelihood);
        }
        return labelScores;
    }

    private Int2DoubleOpenHashMap normaliseProbabilities(Int2DoubleOpenHashMap map) {
        double sum = 0.;

        // Collect sum
        for (double d : map.values()) {
            sum += d;
        }

        // Divide by sum
        for (int k : map.keySet()) {
            map.put(k, map.get(k) / sum);
        }

        return map;
    }

	private void initLabels()
	{
		if (this.labels.size() > 2) {
			System.err.println("*** [WARNING]: NaiveBayesClassifierFeatureMarginals called with more than 2 target labels! ***");
		} else if (this.labels.size() < 2) {
			System.err.println("*** [WARNING]: NaiveBayesClassifierFeatureMarginals called with less than 2 target labels! ***");
		}

		int[] l = this.labels.toIntArray();
		this.posLabel = l[0];
		this.otherLabel = l[1];
	}
}