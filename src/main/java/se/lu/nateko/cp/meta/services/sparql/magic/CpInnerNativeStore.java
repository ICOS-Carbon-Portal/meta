package se.lu.nateko.cp.meta.services.sparql.magic;

import java.nio.file.Path;
import java.util.function.Supplier;

import org.eclipse.rdf4j.sail.nativerdf.CpNativeStoreConnection;
import org.eclipse.rdf4j.sail.nativerdf.NativeStore;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.SailConnectionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.lu.nateko.cp.meta.services.sparql.magic.StatementsEnricher;

class CpInnerNativeStore extends NativeStore{

	public SailConnectionListener listener = null;
	public StatementsEnricher enricher = null;
	private Boolean useCpConnection = false;
	private String readonlyErrMessage = null;
	private static final Logger logger = LoggerFactory.getLogger(NativeStore.class);

	public CpInnerNativeStore(Path storageFolder, String indexDef) {
		super(storageFolder.toFile(), indexDef);
	}

	public synchronized void makeReadonly(String errMessage){
		logger.info("Switching CpInnerNativeStore to read-only mode");
		readonlyErrMessage = errMessage;
	}

	public synchronized boolean isReadonly(){
		return readonlyErrMessage != null;
	}

	public synchronized SailConnection getCpConnection(){
		useCpConnection = true;
		try{
			NotifyingSailConnection writable = getConnection();

			if(readonlyErrMessage == null) {
				if(listener != null) writable.addConnectionListener(listener);
				return writable;
			}
			//no need to update SPARQL index if connection is read-only, hence no listening
			return new ReadonlyConnectionWrapper(writable, this.readonlyErrMessage);
		} finally{
			useCpConnection = false;
		}
	}

	@Override
	protected NotifyingSailConnection getConnectionInternal() throws SailException{
		if(useCpConnection && enricher != null) {
			try {
				return new CpNativeStoreConnection(this, enricher);
			}
			catch(Exception e){
				throw new SailException(e);
			}
		} else {
			return super.getConnectionInternal();
		}
	}

}
