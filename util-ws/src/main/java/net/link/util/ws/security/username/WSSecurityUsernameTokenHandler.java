package net.link.util.ws.security.username;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPPart;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;
import net.link.util.InternalInconsistencyException;
import net.link.util.logging.Logger;
import net.link.util.pkix.ServerCrypto;
import net.link.util.ws.security.SOAPUtils;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSPasswordCallback;
import org.apache.ws.security.WSSecurityEngine;
import org.apache.ws.security.WSSecurityEngineResult;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.message.WSSecHeader;
import org.apache.ws.security.message.WSSecUsernameToken;
import org.apache.ws.security.message.token.UsernameToken;


/**
 * Created by wvdhaute
 * Date: 22/01/14
 * Time: 13:08
 */
public class WSSecurityUsernameTokenHandler implements SOAPHandler<SOAPMessageContext> {

    private static final Logger logger = Logger.get( WSSecurityUsernameTokenHandler.class );

    public static final String USERNAME_PROPERTY = WSSecurityUsernameTokenHandler.class + ".username";
    public static final String PASSWORD_PROPERTY = WSSecurityUsernameTokenHandler.class + ".password";

    private WSSecurityUsernameTokenCallback callback;

    public WSSecurityUsernameTokenHandler() {
        // do nothing, needed tho
    }

    public WSSecurityUsernameTokenHandler(WSSecurityUsernameTokenCallback callback) {

        this.callback = callback;
    }

    /**
     * Override this method to supply the WS-Security config callback
     */
    protected WSSecurityUsernameTokenCallback loadWsSecurityUsernameTokenCallback() {

        return null;
    }

    private WSSecurityUsernameTokenCallback getWsSecurityUsernameTokenCallback() {

        if (null == callback) {
            callback = loadWsSecurityUsernameTokenCallback();
        }

        if (null == callback) {
            throw new InternalInconsistencyException(
                    "No WS-Security callback was found, either install the handler @ runtime to provide one or extend this handler and override the loadWsSecurityUsernameTokenCallback method." );
        }

        return callback;
    }

    @PostConstruct
    public void postConstructCallback() {

        System.setProperty( "com.sun.xml.ws.fault.SOAPFaultBuilder.disableCaptureStackTrace", Boolean.toString( true ) );
    }

    @Override
    public Set<QName> getHeaders() {

        return ImmutableSet.of( new QName( "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd", "Security" ) );
    }

    @Override
    public void close(final MessageContext messageContext) {

    }

    @Override
    public boolean handleFault(final SOAPMessageContext soapMessageContext) {

        return true;
    }

    @Override
    public boolean handleMessage(final SOAPMessageContext soapMessageContext) {

        SOAPPart soapPart = soapMessageContext.getMessage().getSOAPPart();
        boolean isOutbound = (Boolean) soapMessageContext.get( MessageContext.MESSAGE_OUTBOUND_PROPERTY );

        if (isOutbound)
            return handleOutboundDocument( soapPart );
        else
            return handleInboundDocument( soapPart, soapMessageContext );
    }

    /**
     * Handles the outbound SOAP message. Adds the WS Security Header containing the username token
     */
    private boolean handleOutboundDocument(SOAPPart document) {

        String username = getWsSecurityUsernameTokenCallback().getUsername();

        if (null == username) {
            logger.dbg( "No username provided, not adding WS-Security SOAP header" );
            return true;
        }

        logger.dbg( "Out: Adding WS-Security SOAP header" );

        try {
            WSSecHeader wsSecHeader = new WSSecHeader();
            wsSecHeader.insertSecurityHeader( document );

            WSSecUsernameToken wsSecUsernameToken = new WSSecUsernameToken();
            wsSecUsernameToken.setUserInfo( username, getWsSecurityUsernameTokenCallback().getPassword() );
            if (getWsSecurityUsernameTokenCallback().addNonce()) {
                wsSecUsernameToken.addCreated();
                wsSecUsernameToken.addNonce();
            }
            if (getWsSecurityUsernameTokenCallback().isDigestPassword()) {
                wsSecUsernameToken.setPasswordType( WSConstants.PASSWORD_DIGEST );
            } else {
                wsSecUsernameToken.setPasswordType( WSConstants.PASSWORD_TEXT );
            }
            wsSecUsernameToken.prepare( document );
            wsSecUsernameToken.build( document, wsSecHeader );
        }
        catch (WSSecurityException e) {
            logger.err( e, "While handling outbound WS request" );
            return false;
        }

        return true;
    }

    /**
     * Handles the inbound SOAP message. Puts the username,password on the @{link SOAPMessageContext}
     */
    private boolean handleInboundDocument(SOAPPart document, final SOAPMessageContext soapMessageContext) {

        logger.dbg( "In: WS-Security header validation" );

        List<WSSecurityEngineResult> wsSecurityEngineResults;
        try {
            //noinspection unchecked
            wsSecurityEngineResults = new WSSecurityEngine().processSecurityHeader( document, null, new CallbackHandler() {

                @Override
                public void handle(Callback[] callbacks)
                        throws IOException, UnsupportedCallbackException {

                    for (Callback c : callbacks) {
                        if (c instanceof WSPasswordCallback) {
                            WSPasswordCallback wspc = (WSPasswordCallback) c;

                            String password = getWsSecurityUsernameTokenCallback().handle( wspc.getIdentifier() );
                            if (null == password) {
                                throw SOAPUtils.createSOAPFaultException(
                                        String.format( "Username \"%s\" is unknown, invalid security header", wspc.getIdentifier() ), "FailedCheck" );
                            }

                            wspc.setPassword( password );

                            setUsername( soapMessageContext, wspc.getIdentifier() );
                            setPassword( soapMessageContext, password );
                        } else {
                            throw new UnsupportedCallbackException( c, "Unrecognized Callback" );
                        }
                    }
                }
            }, new ServerCrypto() );
        }
        catch (WSSecurityException e) {
            throw SOAPUtils.createSOAPFaultException( "The security header was invalid", "FailedCheck", e );
        }
        logger.dbg( "results: %s", wsSecurityEngineResults );

        if (null == wsSecurityEngineResults && getWsSecurityUsernameTokenCallback().isInboundHeaderOptional()) {
            logger.dbg( "No username token found on WS-Security header..." );
            return true;
        } else if (null == wsSecurityEngineResults) {
            throw SOAPUtils.createSOAPFaultException( "Missing security header", "FailedCheck" );
        }

        UsernameToken ut = null;
        for (WSSecurityEngineResult result : wsSecurityEngineResults) {

            ut = (UsernameToken) result.get( WSSecurityEngineResult.TAG_USERNAME_TOKEN );
        }

        if (null == ut) {
            logger.err( "No username token found..." );
            return false;
        }

        logger.dbg( "Username: \"%s\"", ut.getName() );
        logger.dbg( "Password: \"%s\"", ut.getPassword() );
        return true;
    }

    private static void setUsername(SOAPMessageContext context, String username) {

        context.put( USERNAME_PROPERTY, username );
        context.setScope( USERNAME_PROPERTY, MessageContext.Scope.APPLICATION );
    }

    private static void setPassword(SOAPMessageContext context, String password) {

        context.put( PASSWORD_PROPERTY, password );
        context.setScope( PASSWORD_PROPERTY, MessageContext.Scope.APPLICATION );
    }

    /**
     * @return the username in the WS-Security username token, set by the {@link WSSecurityUsernameTokenHandler}.
     */
    @SuppressWarnings("unchecked")
    public static String findUsername(MessageContext context) {

        return (String) context.get( USERNAME_PROPERTY );
    }

    /**
     * @return the password (plaintext) in the WS-Security username token, set by the {@link WSSecurityUsernameTokenHandler}.
     */
    @SuppressWarnings("unchecked")
    public static String findPassword(MessageContext context) {

        return (String) context.get( PASSWORD_PROPERTY );
    }

    /**
     * Adds a new WS-Security client handler to the handler chain of the given JAX-WS port.
     */
    public static void install(final BindingProvider port, final WSSecurityUsernameTokenCallback callback) {

        @SuppressWarnings("unchecked")
        List<Handler> handlerChain = port.getBinding().getHandlerChain();
        handlerChain.add( new WSSecurityUsernameTokenHandler( callback ) );
        port.getBinding().setHandlerChain( handlerChain );
    }
}
