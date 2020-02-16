package picard.arrays.illumina;

import htsjdk.samtools.util.IOUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import picard.arrays.VcfToAdpc;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class VcfToAdpcTest {

    private static final File TEST_DATA_DIR = new File("testdata/picard/arrays/illumina");
    private static final File TEST_VCF = new File(TEST_DATA_DIR, "TestVcfToAdpc.vcf");
    private static final File SINGLE_SAMPLE_VCF = new File(TEST_DATA_DIR, "TestAdpc1.vcf");
    private static final File MULTI_SAMPLE_VCF = new File(TEST_DATA_DIR, "TestAdpc23.vcf");

    private static final File TEST_EXPECTED_ADPC_BIN_FILE = new File(TEST_DATA_DIR, "TestIlluminaAdpcFileWriter.adpc.bin");
    // Test with a single sample VCF as input.
    private static final File EXPECTED_SINGLE_SAMPLE_ADPC_BIN_FILE = new File(TEST_DATA_DIR, "TestAdpc1.adpc.bin");
    // Test with a multi sample VCF as input
    private static final File EXPECTED_MULTI_SAMPLE_ADPC_BIN_FILE = new File(TEST_DATA_DIR, "TestAdpc23.adpc.bin");
    // Test with both a single sample VCF and multi sample VCF as input.  In that order
    private static final File EXPECTED_S_M_ADPC_BIN_FILE = new File(TEST_DATA_DIR, "TestAdpc1_23.adpc.bin");
    // Test with both a multi sample VCF and single sample VCF as input.  In that order
    private static final File EXPECTED_M_S_ADPC_BIN_FILE = new File(TEST_DATA_DIR, "TestAdpc23_1.adpc.bin");

    @DataProvider(name = "vcfToAdpcBinCombinations")
    public Object[][] vcfToAdpcBinCombinations() {
        return new Object[][]{
                {Collections.singletonList(TEST_VCF), TEST_EXPECTED_ADPC_BIN_FILE},
                {Collections.singletonList(SINGLE_SAMPLE_VCF), EXPECTED_SINGLE_SAMPLE_ADPC_BIN_FILE},
                {Collections.singletonList(MULTI_SAMPLE_VCF), EXPECTED_MULTI_SAMPLE_ADPC_BIN_FILE},
                {Arrays.asList(SINGLE_SAMPLE_VCF, MULTI_SAMPLE_VCF), EXPECTED_S_M_ADPC_BIN_FILE},
                {Arrays.asList(MULTI_SAMPLE_VCF, SINGLE_SAMPLE_VCF), EXPECTED_M_S_ADPC_BIN_FILE}
        };
    }

    @Test(dataProvider = "vcfToAdpcBinCombinations")
    public void testVcfToAdpc(final List<File> vcfs, final File expectedAdpcBinFile) throws IOException {
        final File output = File.createTempFile("testIlluminaAdpcFileWriter.", ".adpc.bin");
        output.deleteOnExit();

        final VcfToAdpc vcfToAdpc = new VcfToAdpc();
        vcfToAdpc.VCF = vcfs;
        vcfToAdpc.OUTPUT = output;

        Assert.assertEquals(vcfToAdpc.instanceMain(new String[0]), 0);

        IOUtil.assertFilesEqual(expectedAdpcBinFile, output);
    }

    @Test
    public void testVcfToAdpcFailOnDifferingNumberOfLoci() throws IOException {
        final File output = File.createTempFile("testIlluminaAdpcFileWriter.", ".adpc.bin");
        output.deleteOnExit();

        final VcfToAdpc vcfToAdpc = new VcfToAdpc();
        vcfToAdpc.VCF = Arrays.asList(TEST_VCF, SINGLE_SAMPLE_VCF);
        vcfToAdpc.OUTPUT = output;

        Assert.assertEquals(vcfToAdpc.instanceMain(new String[0]), 1);
    }
}
