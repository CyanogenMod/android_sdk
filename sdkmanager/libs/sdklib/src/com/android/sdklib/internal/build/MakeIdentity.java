/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.internal.build;

import com.android.appauth.Certificate;
import com.android.sdklib.ISdkLog;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.CertificateException;

public class MakeIdentity {

    private final String mAccount;
    private final String mKeystorePath;
    private final String mKeystorePass;
    private final String mAliasName;
    private final String mAliasPass;

    /**
     * Create a {@link MakeIdentity} object.
     * @param account the google account
     * @param keystorePath the path to the keystore
     * @param keystorePass the password of the keystore
     * @param aliasName the key alias name
     * @param aliasPass the key alias password
     */
    public MakeIdentity(String account, String keystorePath, String keystorePass,
            String aliasName, String aliasPass) {
        mAccount = account;
        mKeystorePath = keystorePath;
        mKeystorePass = keystorePass;
        mAliasName = aliasName;
        mAliasPass = aliasPass;
    }

    /**
     * Write the identity file to the given {@link PrintStream} object.
     * @param ps the printstream object to write the identity file to.
     * @return true if success.
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws IOException
     * @throws UnrecoverableEntryException
     */
    public boolean make(PrintStream ps, ISdkLog log)
            throws KeyStoreException, NoSuchAlgorithmException,
            CertificateException, IOException, UnrecoverableEntryException {

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        FileInputStream fis = new FileInputStream(mKeystorePath);
        keyStore.load(fis, mKeystorePass.toCharArray());
        fis.close();
        PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(
                mAliasName, new KeyStore.PasswordProtection(mAliasPass.toCharArray()));

        if (entry == null) {
            return false;
        }

        Certificate c = new Certificate();
        c.setVersion(Certificate.VERSION);
        c.setType(Certificate.TYPE_IDENTITY);
        c.setHashAlgo(Certificate.DIGEST_TYPE);
        c.setPublicKey(entry.getCertificate().getPublicKey());
        c.setEntityName(mAccount);
        c.signWith(c, entry.getPrivateKey());

        /* sanity check */
        if (!c.isSignedBy(c)) {
            System.err.println("signature failed?!");
            return false;
        }

        /* write to the printstream object */
        try {
            c.writeTo(ps);
        } catch (Exception e) {
            log.error(e, null);
        }

        return true;
    }
}
