package picard.arrays.illumina;

import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import org.broadinstitute.barclay.argparser.Argument;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;
import picard.PicardException;
import picard.cmdline.CommandLineProgram;
import picard.cmdline.StandardOptionDefinitions;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple tool to compare two Illumina GTC files.
 */

@CommandLineProgramProperties(
        summary = CompareGtcFiles.USAGE_DETAILS,
        oneLineSummary = "Compares two GTC files.",
        programGroup = picard.cmdline.programgroups.GenotypingArraysProgramGroup.class
)
public class CompareGtcFiles extends CommandLineProgram {

    static final String USAGE_DETAILS =
            "CompareGtcFiles takes two Illumina GTC file and compares their contents to ensure that fields expected to be the same " +
                    "are in fact the same.  This will exclude any variable field, such as a date. " +
                    "The GTC files must be generated on the same chip type. " +
                    "<h4>Usage example:</h4>" +
                    "<pre>" +
                    "java -jar picard.jar CompareGtcFiles \\<br />" +
                    "      INPUT=input1.gtc \\<br />" +
                    "      INPUT=input2.gtc \\<br />" +
                    "      ILLUMINA_NORMALIZATION_MANIFEST=chip_name.bpm.csv \\<br />" +
                    "</pre>";

    private static Log log = Log.getInstance(CompareGtcFiles.class);

    @Argument(shortName = StandardOptionDefinitions.INPUT_SHORT_NAME,
            doc = "GTC input files to compare.",
            minElements = 2,
            maxElements = 2)
    public List<File> INPUT;

    @Argument(shortName = "NORM_MANIFEST", doc = "An Illumina bead pool manifest (a manifest containing the Illumina normalization ids) (bpm.csv)")
    public File ILLUMINA_NORMALIZATION_MANIFEST;

    private List<String> errors = new ArrayList<>();

    //ignored methods
    private static final List<String> IGNORED_METHODS = new ArrayList<>();

    static {
        IGNORED_METHODS.add("getClass");
        IGNORED_METHODS.add("getAutoCallDate");
        IGNORED_METHODS.add("getImagingDate");
        //This is the number of TOC entries. It will be different with different versions.
        IGNORED_METHODS.add("getNumberOfEntries");
        //We don't inject these in our gtcs so they will always be blank and so we don't bother comparing.
        IGNORED_METHODS.add("getSampleName");
        IGNORED_METHODS.add("getSamplePlate");
        IGNORED_METHODS.add("getSampleWell");
    }

    @Override
    protected int doWork() {
        IOUtil.assertFilesAreReadable(INPUT);

        try {
            InfiniumNormalizationManifest infiniumNormalizationManifest
                    = new InfiniumNormalizationManifest(ILLUMINA_NORMALIZATION_MANIFEST);
            InfiniumGTCFile gtcFileOne = new InfiniumGTCFile(new DataInputStream(new FileInputStream(INPUT.get(0))), infiniumNormalizationManifest);
            InfiniumGTCFile gtcFileTwo = new InfiniumGTCFile(new DataInputStream(new FileInputStream(INPUT.get(1))), infiniumNormalizationManifest);
            compareGTCFiles(gtcFileOne, gtcFileTwo);

            //report errors and exit 1 if any are detected.
            if (!errors.isEmpty()) {
                for (String error : errors) {
                    log.error(error);
                }
                return 1;
            }
        } catch (IOException | IllegalAccessException | InvocationTargetException e) {
            throw new PicardException("File error: ", e);
        }
        return 0;
    }

    private void compareGTCFiles(InfiniumGTCFile gtcFileOne, InfiniumGTCFile gtcFileTwo) throws InvocationTargetException, IllegalAccessException {
        //compare all fields we expect won't change.
        Method[] methods = gtcFileOne.getClass().getMethods();
        for (Method method : methods) {
            //skip ignored methods.
            if (IGNORED_METHODS.contains(method.getName())) {
                continue;
            }
            //compare all getters
            if (method.getName().startsWith("get") && method.getGenericParameterTypes().length == 0) {
                //if we have a version and they don't match we just want a warning
                //if getter returns an array compare all array values otherwise do an Object compare.
                //if getter returns an array of arrays do deep compare
                if (method.getName().equals("getFileVersion")) {
                    compareVersions(method.invoke(gtcFileOne), method.invoke(gtcFileTwo));
                } else if (method.getReturnType().isArray() && method.getReturnType().getComponentType().isArray()) {
                    compareArrayOfArrays(method.invoke(gtcFileOne), method.invoke(gtcFileTwo),
                            method.getName());
                } else if (method.getReturnType().isArray()) {
                    compareArrays(method.invoke(gtcFileOne), method.invoke(gtcFileTwo),
                            method.getName());
                } else {
                    compare(method.invoke(gtcFileOne),
                            method.invoke(gtcFileTwo), method.getName());
                }
            }
        }
    }

    private void compareVersions(Object versionOne, Object versionTwo) {
        if (!versionOne.equals(versionTwo)) {
            log.warn(String.format("File versions do not match ( %s vs %s )",
                    versionOne, versionTwo));
        }
    }

    private void compare(Object objectOne, Object objectTwo, String type) {
        if (checkNulls(objectOne, objectTwo, type)) return;

        List<String> compareErrors = new ArrayList<>();

        if (!objectOne.equals(objectTwo)) {
            compareErrors.add(String.format("%s does not match ( %s vs %s )",
                    type, objectOne, objectTwo));
        }
        checkErrors(type, compareErrors);
    }

    private void compareArrays(Object arrayOne, Object arrayTwo, String type) {
        if (checkNulls(arrayOne, arrayTwo, type)) return;

        List<String> compareErrors = new ArrayList<>();
        int differences = arrayDifferences(arrayOne, arrayTwo, type, compareErrors);
        if (differences > 0) {
            compareErrors.add(String.format("%s do not match. %d elements of the array differ.", type, differences));
        }
        checkErrors(type, compareErrors);
    }

    private int arrayDifferences(Object arrayOne, Object arrayTwo, String type, List<String> compareErrors) {
        int length1 = Array.getLength(arrayOne);
        int length2 = Array.getLength(arrayTwo);

        int diffCount = 0;
        if (length1 != length2) {
            compareErrors.add(String.format("%s do not match. Arrays of different lengths. ( %d vs %d )",
                    type, length1, length2));
        } else {
            for (int i = 0; i < length1; i++) {
                //for floats only compare 3 decimal places
                if (arrayOne.getClass().getComponentType() == float.class) {
                    Float float1 = (float) Array.get(arrayOne, i);
                    Float float2 = (float) Array.get(arrayTwo, i);
                    if (float1.equals(Float.NaN) || float2.equals(Float.NaN)) {
                        if (!float1.equals(float2)) diffCount++;
                    } else {
                        BigDecimal decimal1 = BigDecimal.valueOf(float1).setScale(3, BigDecimal.ROUND_DOWN);
                        BigDecimal decimal2 = BigDecimal.valueOf(float2).setScale(3, BigDecimal.ROUND_DOWN);
                        if (!decimal1.equals(decimal2)) {
                            diffCount++;
                        }
                    }
                } else if (!Array.get(arrayOne, i).equals(Array.get(arrayTwo, i))) {
                    diffCount++;
                }
            }
        }
        return diffCount;
    }


    private void compareArrayOfArrays(Object arrayOfArraysOne, Object arrayOfArraysTwo, String type) {
        List<String> compareErrors = new ArrayList<>();

        if (checkNulls(arrayOfArraysOne, arrayOfArraysTwo, type)) return;

        int differences = 0;
        int length1 = Array.getLength(arrayOfArraysOne);
        int length2 = Array.getLength(arrayOfArraysTwo);
        if (length1 != length2) {
            compareErrors.add(String.format("%s do not match. Arrays of different lengths. ( %d vs %d )",
                    type, length1, length2));
        } else {
            //iterate over the first array
            for (int i = 0; i < length1; i++) {
                //iterate over the second array
                Object innerArrayOne = Array.get(arrayOfArraysOne, i);
                Object innerArrayTwo = Array.get(arrayOfArraysTwo, i);
                differences += arrayDifferences(innerArrayOne, innerArrayTwo, type, compareErrors);
            }
            if (differences > 0) {
                compareErrors.add(String.format("%s do not match. %d elements of the array differ.", type, differences));
            }
        }
        checkErrors(type, compareErrors);
    }

    private void checkErrors(String type, List<String> compareErrors) {
        if (compareErrors.size() > 0) {
            errors.addAll(compareErrors);
        } else {
            log.info(type + " IDENTICAL");
        }
    }

    private boolean checkNulls(Object arrayOne, Object arrayTwo, String type) {
        //if one is null we assume a version mismatch
        if (arrayOne == null || arrayTwo == null) {
            log.warn(String.format("Field %s is not in both files. Version mismatch likely", type));
            return true;
        }
        //if they are primitives check for 0 instead of null
        if ((arrayOne.equals(0) ^ arrayTwo.equals(0)) || (arrayOne.equals(0.0f) ^ arrayTwo.equals(0.0f))) {
            log.warn(String.format("Field %s is not in both files. Version mismatch likely", type));
            return true;
        }
        return false;
    }

}
