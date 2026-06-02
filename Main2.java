public class Main2 {
    public static void main(String[] args) {
        String seedString = "PARAMARTHA_SECRET_12345_MASUK";
        int hash = 0;
        for (int i = 0; i < seedString.length(); i++) {
            hash = (hash * 31) + seedString.charAt(i);
        }
        System.out.println("Java Hash: " + hash);
        
        long randomSeed = Integer.toUnsignedLong(hash);
        System.out.println("Java randomSeed 0: " + randomSeed);
        for (int i = 0; i < 5; i++) {
            randomSeed = (randomSeed * 1103515245L + 12345L) & 0xFFFFFFFFL;
            System.out.println("Java randomSeed " + (i+1) + ": " + randomSeed);
        }
    }
}
