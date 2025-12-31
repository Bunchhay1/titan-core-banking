package com.titan.titancorebanking.utils;

import java.time.Year;
import java.util.Random;

public class AccountNumberUtils {

    private static final String BRANCH_CODE = "001"; // Titan HQ
    private static final Random random = new Random();

    public static String generateAccountNumber() {
        String year = String.valueOf(Year.now().getValue());
        // Random 4 ខ្ទង់ (0000-9999)
        String randomPart = String.format("%04d", random.nextInt(10000));

        // លេខដើម ១១ ខ្ទង់
        String rawNumber = BRANCH_CODE + year + randomPart;

        // គណនាលេខទី ១២ (Check Digit)
        int checkDigit = calculateLuhnCheckDigit(rawNumber);

        return rawNumber + checkDigit;
    }

    // រូបមន្តគណនា Luhn Check Digit
    private static int calculateLuhnCheckDigit(String number) {
        int sum = 0;
        boolean alternate = true;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(number.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }
}