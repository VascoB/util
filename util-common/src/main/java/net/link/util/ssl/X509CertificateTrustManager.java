package net.link.util.ssl;

import be.fedict.trust.TrustValidator;
import be.fedict.trust.linker.TrustLinkerResultException;
import be.fedict.trust.repository.MemoryCertificateRepository;
import com.google.common.collect.ObjectArrays;
import com.lyndir.lhunath.opal.system.logging.Logger;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.*;
import net.link.util.common.*;


/**
 * <h2>{@link X509CertificateTrustManager}<br> <sub>[in short].</sub></h2>
 * <p/>
 * <p> <i>03 03, 2011</i> </p>
 *
 * @author lhunath
 */
public class X509CertificateTrustManager implements X509TrustManager {

    static final Logger logger = Logger.get( X509CertificateTrustManager.class );

    private final X509Certificate  trustedCertificate;
    private final X509TrustManager defaultTrustManager;

    public X509CertificateTrustManager() {

        this.trustedCertificate = null;
        this.defaultTrustManager = null;
    }

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

        if (!checkTrustedCertificate( new CertificateChain( chain ), authType ))
            defaultTrustManager.checkClientTrusted( chain, authType );
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {

        if (!checkTrustedCertificate( new CertificateChain( chain ), authType ))
            defaultTrustManager.checkServerTrusted( chain, authType );
    }

    /**
     * @param chain    The request's certificate chain.
     * @param authType The request's authentication type.
     *
     * @return {@code true} if the chain was checked and trusted.  {@code false} if no check could be performed.
     *
     * @throws CertificateException if the chain was checked and was not trusted.
     */
    private boolean checkTrustedCertificate(final CertificateChain chain, final String authType)
            throws CertificateException {

        if (chain.isEmpty())
            return false;

        logger.inf( "checking if chain: \n\n%s (authType: %s) \n\n is trusted (by: %s).", Arrays.asList( chain ), authType, trustedCertificate );

        // If an SSL certificate is given, check the chain against it.
        if (trustedCertificate != null)

            try {
                MemoryCertificateRepository certificateRepository = new MemoryCertificateRepository();
                certificateRepository.addTrustPoint( trustedCertificate );

                if (!chain.hasRootCertificate()) {
                    // root certificate not included, take on assumption trustedCertificate == the rootCertificate
                    chain.addRootCertificate( trustedCertificate );
                }

                TrustValidator trustValidator = new TrustValidator( certificateRepository );
                trustValidator.addTrustLinker( new LazyPublicKeyTrustLinker() );

                trustValidator.isTrusted( chain.getOrderedCertificateChain() );
                return true;
            }
            catch (TrustLinkerResultException e) {

                logger.err( e, "Certificate chain did not validate against trusted certificates.\nChain: %s\n Trusted Certificates: %s\n", chain,
                        trustedCertificate );
                throw new CertificateException( e );
            }

        switch (ApplicationMode.get()) {
            case DEBUG:
            case DEMO:
                return true;
            case DEPLOYMENT:
                return false;
        }

        return false;
    }
}
