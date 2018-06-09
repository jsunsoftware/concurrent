package com.jsunsoft.util;

/* Copyrights 2015 FIP Software
 * Date: 6/9/18.
 * Developer: Beno Arakelyan
 * This software is the proprietary information of FIP Software.
 * Its use is subject to License terms.
 */

@FunctionalInterface
public interface Executable<X extends Throwable> {

    void execute() throws X;
}
