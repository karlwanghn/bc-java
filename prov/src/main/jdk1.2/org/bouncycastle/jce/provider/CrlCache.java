package org.bouncycastle.jce.provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CRL;
import java.security.cert.CRLException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Hashtable;

import org.bouncycastle.jcajce.PKIXCRLStore;
import org.bouncycastle.util.CollectionStore;
import org.bouncycastle.util.Iterable;
import org.bouncycastle.util.Selector;
import org.bouncycastle.util.Store;

class CrlCache
{
    private static final int DEFAULT_TIMEOUT = 15000;

    private static Hashtable cache = new Hashtable();

    static synchronized PKIXCRLStore getCrl(CertificateFactory certFact, Date validDate, URL distributionPoint)
        throws IOException, CRLException
    {
        PKIXCRLStore crlStore = null;

        WeakReference markerRef = (WeakReference)cache.get(distributionPoint);
        if (markerRef != null)
        {
            crlStore = (PKIXCRLStore)markerRef.get();
        }

        if (crlStore != null)
        {
            boolean isExpired = false;
            for (Iterator it = crlStore.getMatches(null).iterator(); it.hasNext();)
            {
                X509CRL crl = (X509CRL)it.next();

                Date nextUpdate = crl.getNextUpdate();
                if (nextUpdate != null && nextUpdate.before(validDate))
                {
                    isExpired = true;
                    break;
                }
            }

            if (!isExpired)
            {
                return crlStore;
            }
        }

        Collection crls;

        //if (distributionPoint.getProtocol().indexOf("ldap") >= 0)
        //{
            //crls = getCrlsFromLDAP(certFact, distributionPoint);
        //}
        //else
        {
            // http, https, ftp
            crls = getCrls(certFact, distributionPoint);
        }

        LocalCRLStore localCRLStore = new LocalCRLStore(new CollectionStore<CRL>(crls));

        cache.put(distributionPoint, new WeakReference(localCRLStore));

        return localCRLStore;
    }

    private static Collection getCrls(CertificateFactory certFact, URL distributionPoint)
        throws IOException, CRLException
    {
        HttpURLConnection crlCon = (HttpURLConnection)distributionPoint.openConnection();
//        crlCon.setConnectTimeout(DEFAULT_TIMEOUT);
//        crlCon.setReadTimeout(DEFAULT_TIMEOUT);

        InputStream crlIn = crlCon.getInputStream();

        Collection crls = certFact.generateCRLs(crlIn);

        crlIn.close();

        return crls;
    }

    private static class LocalCRLStore<T extends CRL>
        implements PKIXCRLStore, Iterable<CRL>
    {
        private Collection<CRL> _local;

        /**
         * Basic constructor.
         *
         * @param collection - initial contents for the store, this is copied.
         */
        public LocalCRLStore(
            Store<CRL> collection)
        {
            _local = new ArrayList<CRL>(collection.getMatches(null));
        }

        /**
         * Return the matches in the collection for the passed in selector.
         *
         * @param selector the selector to match against.
         * @return a possibly empty collection of matching objects.
         */
        public Collection getMatches(Selector selector)
        {
            if (selector == null)
            {
                return new ArrayList<CRL>(_local);
            }
            else
            {
                List<CRL> col = new ArrayList<CRL>();
                Iterator<CRL> iter = _local.iterator();

                while (iter.hasNext())
                {
                    CRL obj = (CRL)iter.next();

                    if (selector.match(obj))
                    {
                        col.add(obj);
                    }
                }

                return col;
            }
        }

        public Iterator<CRL> iterator()
        {
            return getMatches(null).iterator();
        }
    }

    private static class WeakReference
    {
	private Object o;

        WeakReference(Object o)
        {
            this.o = o;
        }

        public Object get()
        {
            return o;
        }
    }
}
