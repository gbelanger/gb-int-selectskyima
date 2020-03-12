import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;

import nom.tam.fits.*;
import nom.tam.util.BufferedDataInputStream;


public final class Utils {

    //  Formating variables
    public static DecimalFormat twoDigits = new DecimalFormat("0.00");
    public static DecimalFormat threeDigits = new DecimalFormat("0.000");
    public static DecimalFormat timeFormat = new DecimalFormat("0.000E0");
    public static String sep = File.separator;

    public static InputStream getFileFromJarAsStream(String name) {
	return ClassLoader.getSystemResourceAsStream(name);
    }

    public static String[] readScwList(File scwlisFile) throws IOException, IntegralException  {
	ScwLis scwlis = new ScwLis(scwlisFile);
	String[] scwNums = scwlis.getScwIDs();
	SelectSkyIma.logger.info("Science window list: "+scwlisFile.getPath()+" has "+scwNums.length+" rows");
	return scwNums;
    }

    public static ArrayList[] getImageFileArrayListFromScwNums(String[] scwNums, String[] dataDirNames) throws IOException {
	SelectSkyIma.logger.info("Selecting ISGRI images according to scw list ...");
	ArrayList filesArrayList = new ArrayList();
	int k=0;
	int i=0;
	ArrayList updatedScwNumsArrayList = new ArrayList();
	while ( i < dataDirNames.length && k < scwNums.length ) {
	    String dirname = dataDirNames[i];
	    String scwNum = scwNums[k];
	    String rev = scwNum.substring(0,4);
	    while ( dirname.contains(sep+rev+sep) ) {
		String name = "isgri_sky_ima.fits.gz";
		String fullDirname = dirname +sep+ scwNum +sep+ "scw" +sep+ scwNum+".001";
		String filename = fullDirname+sep+name;
		File file = new File(filename);
		if ( file.exists() ) {
		    String swgIdxFilenameGZ =  dirname +sep+ scwNum +sep+ "swg_idx_ibis.fits.gz";
		    String swgIdxFilename =  dirname +sep+ scwNum +sep+ "swg_idx_ibis.fits";
		    if ( (new File(swgIdxFilenameGZ)).exists() || (new File(swgIdxFilename)).exists() ) {
			filesArrayList.add(file);
			updatedScwNumsArrayList.add(scwNum);
		    }
		}
		else {
		    name = "isgri_sky_ima.fits";
		    filename = fullDirname+sep+name;
		    file = new File(filename);
		    if ( file.exists() ) {
			String swgIdxFilenameGZ =  dirname +sep+ scwNum +sep+ "swg_idx_ibis.fits.gz";
			String swgIdxFilename =  dirname +sep+ scwNum +sep+ "swg_idx_ibis.fits";
			if ( (new File(swgIdxFilenameGZ)).exists() || (new File(swgIdxFilename)).exists() ) {
				filesArrayList.add(file);
				updatedScwNumsArrayList.add(scwNum);
			}
		    }
		}
		k++;
		try {
		    scwNum = scwNums[k];
		    rev = scwNum.substring(0,4);
		}
		catch ( ArrayIndexOutOfBoundsException e ) {
		    break;
		}
	    }
	    i++;
	}
	filesArrayList.trimToSize();
	updatedScwNumsArrayList.trimToSize();
	SelectSkyIma.logger.info("  "+filesArrayList.size()+" images selected (of "+scwNums.length+" scw in list)");
	return new ArrayList[]{filesArrayList, updatedScwNumsArrayList};
    }

    // openFits
    public static Fits openFits(File file) throws Exception {
	boolean isGzipped = MyFile.isGzipped(file);
	BufferedDataInputStream dis = new BufferedDataInputStream(new FileInputStream(file));
	Fits fitsFile = new Fits(dis, isGzipped);
	return fitsFile;
    }
    public static Fits openFits(String filename) throws Exception {
	return openFits(new File(filename));
    }

    // Mean, Variance and non-zero Minimum
    public static double getMean(double[] _data) {
	double sum = 0;
	int n = 0;
	for ( int i=0; i < _data.length; i++ ) {
	    if ( ! Double.isNaN(_data[i]) ) {
		sum += _data[i];
		n++;
	    }
	}
	double mean = sum/n;
	return mean;
    }

    public static double getVariance(double[] _data) {
	double mean = getMean(_data);
	double sum = 0;
	int n = 0;
	for ( int i=0;  i < _data.length; i++ ) {
	    if ( !Double.isNaN(_data[i]) ) {
		sum += Math.pow(_data[i] - mean, 2);
		n++;
	    }
	}
	double variance = sum/(n-1);
	return variance;
    }

    public static float getNonZeroMin(float[] _data) {
	float min = Float.MAX_VALUE;
	for ( int i=0; i < _data.length; i++ )
	    if ( !Float.isNaN(_data[i]) && _data[i] != 0.0 ) min = Math.min(min, _data[i]);
	return min;
    }

}