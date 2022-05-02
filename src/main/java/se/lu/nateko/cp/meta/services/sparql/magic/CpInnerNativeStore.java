package se.lu.nateko.cp.meta.services.sparql.magic;

import java.nio.file.Path;
import java.util.function.Supplier;

import org.eclipse.rdf4j.sail.nativerdf.CpNativeStoreConnection;
import org.eclipse.rdf4j.sail.nativerdf.NativeStore;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;

import se.lu.nateko.cp.meta.services.citation.CitationProvider;

class CpInnerNativeStore extends NativeStore{

	private Boolean useCpConnection;
	private final Boolean disableMagic;
	private final Supplier<IndexProvider> indexProvThunk;
	private final Supplier<CitationProvider> citProvThunk;

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

	public synchronized SailConnection getSpecificConnection(Boolean cpSpecific){
		this.useCpConnection = cpSpecific;
		SailConnection conn = super.getConnection();
		this.useCpConnection = !this.disableMagic;
		return conn;
	}

	@Override
	protected NotifyingSailConnection getConnectionInternal() {
		if(this.useCpConnection) {
			try {
				CpNativeStoreConnection conn = new CpNativeStoreConnection(this, this.citProvThunk.get());
				conn.addConnectionListener(this.indexProvThunk.get());
				return conn;
			}
			catch(Exception e){
				throw new SailException(e);
			}
		} else {
			return super.getConnectionInternal();
		}
	}

}