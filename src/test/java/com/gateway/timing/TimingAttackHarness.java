import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * RQ2a: Timing-attack validation harness.
 * Compares String.equals() vs MessageDigest.isEqual() for constant-time behavior.
 * Writes results to timing_results.csv with columns: method,byte_position,execution_time_ns
 *
 * Usage: java TimingAttackHarness [num_iterations] [output_file]
 *   num_iterations: number of times to test each position (default: 100)
 *   output_file: CSV output path (default: timing_results.csv)
 */
public class TimingAttackHarness {
    
    private static final int HASH_LENGTH = 32; // SHA-256 produces 32 bytes
    private static final int NUM_POSITIONS = HASH_LENGTH;
    private static final int WARMUP_ITERATIONS = 50;
    
    static class TimingResult {
        String method;
        int bytePosition;
        long executionTimeNs;
        
        TimingResult(String method, int bytePosition, long executionTimeNs) {
            this.method = method;
            this.bytePosition = bytePosition;
            this.executionTimeNs = executionTimeNs;
        }
    }
    
    public static void main(String[] args) {
        int numIterations = 100;
        String outputFile = "timing_results.csv";
        
        if (args.length > 0) {
            numIterations = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            outputFile = args[1];
        }
        
        System.out.println("Timing Attack Harness");
        System.out.println("====================");
        System.out.println("Testing String.equals() vs MessageDigest.isEqual()");
        System.out.println("Iterations per position: " + numIterations);
        System.out.println("Output file: " + outputFile);
        System.out.println();
        
        List<TimingResult> results = new ArrayList<>();
        
        // Generate reference hashes
        byte[] referenceHash = generateHash("reference_key");
        
        // Test each byte position
        for (int bytePos = 0; bytePos < NUM_POSITIONS; bytePos++) {
            // Create a test hash with mismatch at bytePos
            byte[] testHash = generateHash("reference_key");
            testHash[bytePos] = (byte) ((testHash[bytePos] + 1) & 0xFF);
            
            System.out.println("Testing byte position " + bytePos + "...");
            
            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                testStringEquals(referenceHash, testHash);
                testMessageDigestIsEqual(referenceHash, testHash);
            }
            
            // Measure String.equals()
            long[] stringTimes = new long[numIterations];
            for (int i = 0; i < numIterations; i++) {
                long start = System.nanoTime();
                testStringEquals(referenceHash, testHash);
                long end = System.nanoTime();
                stringTimes[i] = end - start;
            }
            
            // Measure MessageDigest.isEqual()
            long[] mdTimes = new long[numIterations];
            for (int i = 0; i < numIterations; i++) {
                long start = System.nanoTime();
                testMessageDigestIsEqual(referenceHash, testHash);
                long end = System.nanoTime();
                mdTimes[i] = end - start;
            }
            
            // Record results
            for (long time : stringTimes) {
                results.add(new TimingResult("String.equals()", bytePos, time));
            }
            for (long time : mdTimes) {
                results.add(new TimingResult("MessageDigest.isEqual()", bytePos, time));
            }
        }
        
        // Write results to CSV
        writeResultsToCSV(results, outputFile);
        
        System.out.println();
        System.out.println("✓ Timing analysis complete!");
        System.out.println("Results written to: " + outputFile);
        System.out.println("Next step: python analyze_timing.py");
    }
    
    /**
     * Test using String.equals() for comparison (NOT constant-time).
     */
    private static boolean testStringEquals(byte[] hash1, byte[] hash2) {
        String hex1 = bytesToHex(hash1);
        String hex2 = bytesToHex(hash2);
        return hex1.equals(hex2);
    }
    
    /**
     * Test using MessageDigest.isEqual() (constant-time comparison).
     */
    private static boolean testMessageDigestIsEqual(byte[] hash1, byte[] hash2) {
        try {
            return MessageDigest.isEqual(hash1, hash2);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Generate SHA-256 hash of input string.
     */
    private static byte[] generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Convert bytes to hexadecimal string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Write timing results to CSV file.
     */
    private static void writeResultsToCSV(List<TimingResult> results, String outputFile) {
        try (FileWriter writer = new FileWriter(outputFile)) {
            // Write header
            writer.append("method,byte_position,execution_time_ns\n");
            
            // Write data
            for (TimingResult result : results) {
                writer.append(result.method).append(",")
                      .append(String.valueOf(result.bytePosition)).append(",")
                      .append(String.valueOf(result.executionTimeNs)).append("\n");
            }
            
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
}
