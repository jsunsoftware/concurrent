package com.jsunsoft.util.concurrent;

/* Copyrights 2015 FIP Software
 * Date: 6/9/18.
 * Developer: Beno Arakelyan
 * This software is the proprietary information of FIP Software.
 * Its use is subject to License terms.
 */

public class Test {
    public static void main(String[] args) throws InterruptedException {
        Lock lock = new StripedLock(1, 1);
        lock.lock("", () -> {

            throw new InterruptedException();
        });
    }
}
