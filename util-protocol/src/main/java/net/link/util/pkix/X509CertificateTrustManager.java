package net.link.util.pkix;

import com.google.common.collect.ObjectArrays;
import com.lyndir.lhunath.lib.system.logging.Logger;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;


/**
 * <h2>{@link X509CertificateTrustManager}<br> <sub>[in short] (TODO).</sub></h2>
 *
 * <p> <i>03 03, 2011</i> </p>
 *
 * @author lhunath
 */
public class X509CertificateTrustManager implements X509TrustManager {

    static final Logger logger = Logger.get( X509CertificateTrustManager.class );

    private final X509Certificate  trustedCertificate;
    private final X509TrustManager defaultTrustManager;

    public X509CertificateTrustManager(final X509Certificate trustedCertificate) {

        this.trustedCertificate = trustedCertificate;

        X509TrustManager x509TrustManager = null;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
            trustManagerFactory.init( (KeyStore) null );
            for (TrustManager trustManager : trustManagerFactory.getTrustManagers())
                if (trustManager instanceof X509TrustManager) {
                    x509TrustManager = (X509TrustManager) trustManager;
                    break;
                }
        }
        catch (GeneralSecurityException ignored) {
        }
        defaultTrustManager = x509TrustManager;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {

        X509Certificate[] acceptedIssuers = defaultTrustManager.getAcceptedIssuers();
        if (trustedCertificate != null)
            acceptedIssuers = ObjectArrays.concat( acceptedIssuers, trustedCertificate );

        return acceptedIssuers;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {

        if (!checkTrustedCertificate( chain, authType ))
            defaultTrustManager.checkClientTrusted( chain, authType );
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {

        if (!checkTrustedCertificate( chain, authType ))
            defaultTrustManager.checkServerTrusted( chain, authType );
    }

    /**
     * @param chain    The request's certificate chain.
     * @param authType The request's authentication type.
     *
     * @return <code>true</code> if the chain was checked and trusted.  <code>false</code> if no check could be performed.
     *
     * @throws CertificateException if the chain was checked and was not trusted.
     */
    private boolean checkTrustedCertificate(final X509Certificate[] chain, final String authType)
            throws CertificateException {

        if (chain.length < 1)
            return false;

        logger.inf( "checking if chain: %s (authType: %s) is trusted (by: %s).", Arrays.asList( chain ), authType, trustedCertificate );

        // Check validity of end certificate.
        X509Certificate endCertificate = chain[0];
        endCertificate.checkValidity();

        // If an SSL certificate is given, check the chain against it.
        if (trustedCertificate != null)
            try {
                endCertificate.verify( trustedCertificate.getPublicKey() );
                return true;
            }
            catch (GeneralSecurityException e) {
                throw new CertificateException( e );
            }

        return false;
    }
}