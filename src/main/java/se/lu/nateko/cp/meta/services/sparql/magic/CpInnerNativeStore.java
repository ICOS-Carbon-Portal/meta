package se.lu.nateko.cp.meta.services.sparql.magic;

import java.nio.file.Path;
import java.util.function.Supplier;

import org.eclipse.rdf4j.sail.nativerdf.CpNativeStoreConnection;
import org.eclipse.rdf4j.sail.nativerdf.NativeStore;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.lu.nateko.cp.meta.services.citation.CitationProvider;

class CpInnerNativeStore extends NativeStore{

	private Boolean useCpConnection;
	private String readonlyErrMessage = null;
	private final Boolean disableMagic;
	private final Supplier<IndexProvider> indexProvThunk;
	private final Supplier<CitationProvider> citProvThunk;
	private static final Logger logger = LoggerFactory.getLogger(NativeStore.class);

	public CpInnerNativeStore(
		Path storageFolder,
		String indexDef,
		Boolean disableMagic,
		Supplier<IndexProvider> indexProvThunk,
		Supplier<CitationProvider> citProvThunk
	) {
		super(storageFolder.toFile(), indexDef);

		this.disableMagic = disableMagic;
		this.useCpConnection = !disableMagic;
		this.indexProvThunk = indexProvThunk;
		this.citProvThunk = citProvThunk;

		if(!disableMagic) setEvaluationStrategyFactory(
			new CpEvaluationStrategyFactory(getFederatedServiceResolver(), () -> indexProvThunk.get().index())
		);
	}

	public void makeReadonly(String errMessage){
		logger.info("Switching CpInnerNativeStore to read-only mode");
		this.readonlyErrMessage = errMessage;
	}

	public synchronized SailConnection getSpecificConnection(Boolean cpSpecific){
		this.useCpConnection = cpSpecific;
		try{
			NotifyingSailConnection writable = getConnection();

			if(this.readonlyErrMessage == null) {
				writable.addConnectionListener(this.indexProvThunk.get());
				return writable;
			}

			return new ReadonlyConnectionWrapper(writable, this.readonlyErrMessage);
		} finally{
			this.useCpConnection = !this.disableMagic;
		}
	}

	@Override
	protected NotifyingSailConnection getConnectionInternal() throws SailException{
		if(this.useCpConnection) {
			try {
				return new CpNativeStoreConnection(this, this.citProvThunk.get());
			}
			catch(Exception e){
				throw new SailException(e);
			}
		} else {
			return super.getConnectionInternal();
		}
	}

}
