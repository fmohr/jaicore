package jaicore.ml.tsc.filter;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math.complex.Complex;

import jaicore.ml.tsc.dataset.TimeSeriesDataset;
import jaicore.ml.tsc.exceptions.NoneFittedFilterExeception;

/**
 * @author Helen Beierling
 * 
 *	Rafiei, D., and Mendelzon, A. Efficient retrieval of similar time sequences using DFT.
 *	(1998), pp. 249�257. (1)
 *
 *	Sch�fer, P.: The BOSS is concerned with time series classification in the presence of noise. DMKD (2015)
 *	p.1510 (2)
 */
public class DFT implements IFilter {

	/**
	 * Is used to save the final DFT Coefficients matrices. Each entry in the list corresponds to one 
	 * matrix in the original dataset.
	 */
	private ArrayList<double[][]> DFTCoefficients = new ArrayList<double[][]>();
	
	private double[][] DFTCoefficientsMatrix;
	
	private double[] DFTCoefficientsInstance;
	/**
	 * default value for the computation of the DFT Coefficients normally set to the wordlength/2
	 */
	private int numberOfDisieredCoefficients = 10; 
	
	/**
	 * tracks weather the number of the desired coefficients is set manually
	 */
	private boolean variableSet = false;
	
	/**
	 * tracks whether the fit method was called
	 */
	private boolean fittedInstance = false;
	private boolean fittedMatrix = false;
	private boolean fitted = false;
	
	private boolean meanCorrected = false;
	private int startingpoint = 0;
	
	/**
	 *  The variable is set to 1/sqrt(n) in paper "Efficient Retrieval of Similar Time Sequences Using DFT" by Davood Rafieidrafiei and Alberto Mendelzon
	 *  but in the original "The BOSS is concerned with time series classification in the presence of noise" by Patrick Sch�fer
	 *  it is set to 1/n. By default it is set to 1/n. 
	 */ 
	private double paperSpecificVariable;   
	
	public void setPaperSpecificVariable(double paperSpecificVariable) {
		this.paperSpecificVariable = paperSpecificVariable;
		variableSet = true;
	}

	public void setNumberOfDisieredCoefficients(int numberOfDisieredCoefficients) {
		this.numberOfDisieredCoefficients = numberOfDisieredCoefficients;
	}
	
	
	public void setMeanCorrected(boolean meanCorrected) {
		this.meanCorrected = meanCorrected;
	}

	/* (non-Javadoc)
	 * @see jaicore.ml.tsc.filter.IFilter#transform(jaicore.ml.tsc.dataset.TimeSeriesDataset)
	 * 
	 * Returns a new  DFT dataset according to the by fit calculated DFT coefficents.
	 *
	 */
	@Override
	public TimeSeriesDataset transform(TimeSeriesDataset input) throws IllegalArgumentException, NoneFittedFilterExeception {
		
		if(input.isEmpty()) {
			throw new IllegalArgumentException("This method can not work with an empty dataset.");
		}
		
		if(!fitted) {
			throw new NoneFittedFilterExeception("The fit method must be called before the transform method is called.");
		}
		//creates a new dataset out of the matrix vice Arraylist of the DFT coefficents calculated by fit
		TimeSeriesDataset output = new TimeSeriesDataset(DFTCoefficients, null, null);
		
		return output;
	}

	//calculates the number of desired DFT coefficients for each matrix and therefore for each instance 
	@Override
	public void fit(TimeSeriesDataset input) throws IllegalArgumentException, NoneFittedFilterExeception{
		
		if(input.isEmpty()) {
			throw new IllegalArgumentException("This method can not work with an empty dataset.");
		}
		
		
		for(int matrix = 0; matrix < input.getNumberOfVariables(); matrix++) {
			fitTransform(input.getValues(matrix));
			fittedMatrix = false;
			DFTCoefficients.add(DFTCoefficientsMatrix);
		}
		
		fitted = true;
	}

	@Override
	public TimeSeriesDataset fitTransform(TimeSeriesDataset input) throws IllegalArgumentException, NoneFittedFilterExeception {
		fit(input);
		return transform(input);
	}

	
	@Override
	public double[] transform(double[] input) throws IllegalArgumentException, NoneFittedFilterExeception {
		if(!fitted) {
			throw new NoneFittedFilterExeception("The fit method must be called before the transform method.");
		}
		if(input.length == 0) {
			throw new IllegalArgumentException("The input can not be empty.");
		}
		return DFTCoefficientsInstance;
	}

	@Override
	public void fit(double[] input) throws IllegalArgumentException{
		
		if(numberOfDisieredCoefficients > input.length) {
			throw new IllegalArgumentException("There cannot be more DFT coefficents calcualated than there entrys in the basis instance.");
		}
		
		if(input.length == 0) {
			throw new IllegalArgumentException("The to transform instance can not be of length zero.");
		}
		
		if(!variableSet) {
			paperSpecificVariable = (double) 1.0/((double)input.length);
		}
		
		//The buffer for the calculated DFT coefficeients
		DFTCoefficientsInstance = new double[numberOfDisieredCoefficients*2-(startingpoint*2)];
		
		//Variable used to make steps of size two in a loop that makes setps of size one
		int loopcounter = 0;
		if(meanCorrected) {
			startingpoint = 1;
		}
		
		for(int entry = 0; entry < input.length; entry++) {
			
			Complex result = new Complex(0,0);
			Complex tmp = null;
			
			for(int coefficient = startingpoint; coefficient<numberOfDisieredCoefficients; coefficient++) {
				double currentEntry = input[entry];
				
				//calculates the real and imaginary part of the entry according to the desired coefficient
				//c.f. p. 1510 "The BOSS is concerned with time series classification in the presence of noise" by Patrick Sch�fer
				double realpart = Math.cos(-(1.0/(double)input.length)*2.0*Math.PI*(double)entry*(double)coefficient);
				double imaginarypart =  Math.sin(-(1.0/(double)input.length)*2.0*Math.PI*(double)entry*(double)coefficient);
				
				tmp = new Complex(realpart,imaginarypart);
				tmp = tmp.multiply(currentEntry);
				
				result = result.add(tmp);
			}
			
			result = result.multiply(paperSpecificVariable);
			
			//saves the calculated coefficient in the buffer with first the real part and than the imaginary
			DFTCoefficientsInstance[loopcounter]= result.getReal();
			DFTCoefficientsInstance[loopcounter+1] = result.getImaginary();
			
			loopcounter+=2;
		}
		fittedInstance = true;
	}

	@Override
	public double[] fitTransform(double[] input)  throws IllegalArgumentException, NoneFittedFilterExeception {
		fit(input);
		return transform(input) ;
	}

	@Override
	public double[][] transform(double[][] input) throws IllegalArgumentException, NoneFittedFilterExeception {
		if(!fittedMatrix) {
			throw new NoneFittedFilterExeception("The fit method must be called before transforming");
		}
		if(input.length == 0) {
			throw new IllegalArgumentException("The input can not be empty");
		}
		return DFTCoefficientsMatrix;
	}

	@Override
	public void fit(double[][] input) throws IllegalArgumentException {
		DFTCoefficientsMatrix = new double[input.length][numberOfDisieredCoefficients*2];
		double[] DFTCoefficientsOFInstance = null;
		for(int instance = 0; instance<input.length; instance++) {
			try {
				DFTCoefficientsOFInstance = fitTransform(input[instance]);
			} catch (NoneFittedFilterExeception e) {
				e.printStackTrace();
			}
			fittedInstance = false;
			DFTCoefficientsMatrix[instance] = DFTCoefficientsOFInstance;
		}
		fittedMatrix = true;
	}
	
	@Override
	public double[][] fitTransform(double[][] input) throws IllegalArgumentException, NoneFittedFilterExeception {
		fit(input);
		return transform(input);
	}
	
	// It is required that the input is inform of the already sliced windows. 
	// cf. p. 1516 "The BOSS is concerned with time series classification in the presence of noise" by Patrick Sch�fer
	public double[][] rekursivDFT(double[][] input) {
		if(input.length == 0) {
			throw new IllegalArgumentException("The input can not be empty");
		}
		
		if(input[0].length < numberOfDisieredCoefficients) {
			throw new IllegalArgumentException("Can not compute more dft coefficents than the length of the input.");
		}
		
		if(numberOfDisieredCoefficients < 0) {
			throw new IllegalArgumentException("The number of desiered DFT coefficients can not be negativ.");
		}
		
		Complex[][] outputComplex = new Complex[input.length][numberOfDisieredCoefficients];
		Complex[][] vMatrix = new Complex[numberOfDisieredCoefficients][numberOfDisieredCoefficients];
		for(int i = 0; i < numberOfDisieredCoefficients; i++) {
			vMatrix[i][i] = vFormular(i, input[0].length);
		}
		
		for(int i = 0; i < input.length; i++) {
			if(i == 0) {
				try {
					double[] tmp = fitTransform(input[i]);
					Complex[] firstEntry = new Complex[numberOfDisieredCoefficients];
					for(int entry = 0; entry < tmp.length-1; entry++) {
						firstEntry[entry] = new Complex(tmp[entry], tmp[entry+1]);
					}
					outputComplex[0] = firstEntry;
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoneFittedFilterExeception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			else {
				Complex [] coefficientsForInstance = new Complex[numberOfDisieredCoefficients];
				for(int j = 0; j < numberOfDisieredCoefficients; j++) {
					coefficientsForInstance[j] = vMatrix[j][j].multiply((outputComplex[i-1][j].subtract(new Complex(input[i-1][0],0).add(new Complex(input[i][input[i].length-1],0)))));
				}
				outputComplex[i] = coefficientsForInstance;
			}
		}
		
		double[][] output = conversion(outputComplex);
		return output;
		
	}
	
	private double[][] conversion(Complex[][] input) {
		double[][] output = new double[input.length][input.length*2];
		for(int i = 0; i< input.length; i++) {
			int loopcounter = 0;
			for(int j = 0; j <input[i].length*2; j+=2) {
				output[i][j] = input[i][loopcounter].getReal();
				output[i][j+1] = input[i][loopcounter].getImaginary();
				loopcounter++;
			}
		}
		return output;
	}

	private Complex vFormular(int coefficient, int legthOfinstance) {
		Complex result = new Complex(Math.cos(2*Math.PI*coefficient/legthOfinstance),Math.sin(2*Math.PI*coefficient/legthOfinstance));
		return result;
	}
	public TimeSeriesDataset rekursivDFT(TimeSeriesDataset input) {
		TimeSeriesDataset output = new TimeSeriesDataset(new ArrayList(),null,null);
		for(int matrix = 0; matrix < input.getNumberOfVariables(); matrix++) {
			output.getValueMatrices().add(rekursivDFT(input.getValues(matrix)));
		}
		return output;
	}
}
