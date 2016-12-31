/*
Copyright (C) 2016 Stephan Seifermann

This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the
Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
*/
package io.github.seiferma.jameica.hibiscus.dkb.creditcard.synchronize;

import java.io.Closeable;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.synchronize.AbstractSynchronizeBackend;
import de.willuhn.jameica.hbci.synchronize.SynchronizeBackend;
import de.willuhn.jameica.hbci.synchronize.SynchronizeEngine;
import de.willuhn.logging.Logger;
import de.willuhn.util.ProgressMonitor;
import io.github.seiferma.jameica.hibiscus.dkb.creditcard.account.DKBScraperAccountProperties;

public class DKBScraperSychronizeBackend extends AbstractSynchronizeBackend<DKBScraperSynchronizeJobProvider> {

	private static final String NAME = "DKB Scraper";

	@Resource
	private SynchronizeEngine engine = null;

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public List<String> getPropertyNames(Konto k) {
		try {
			if (k == null || k.hasFlag(Konto.FLAG_DISABLED))
				return null;

			return DKBScraperAccountProperties.getMetaDataNames();
		} catch (RemoteException re) {
			Logger.error("unable to determine property-names", re);
			return null;
		}
	}

	@Override
	protected Class<DKBScraperSynchronizeJobProvider> getJobProviderInterface() {
		return DKBScraperSynchronizeJobProvider.class;
	}

	@Override
	protected AbstractSynchronizeBackend<DKBScraperSynchronizeJobProvider>.JobGroup createJobGroup(Konto k) {
		return new DKBScraperJobGroup(k);
	}

	@Override
	public List<Konto> getSynchronizeKonten(Konto k) {
		return super.getSynchronizeKonten(k).stream().filter(this::supports).collect(Collectors.toList());
	}

	private boolean supports(Konto konto) {
		try {
			if (konto == null || konto.hasFlag(Konto.FLAG_DISABLED) || konto.hasFlag(Konto.FLAG_OFFLINE)) {
				return false;
			}
		} catch (RemoteException re) {
			Logger.error("unable to determine synchronization support for konto", re);
			return false;
		}

		SynchronizeBackend backend = engine.getBackend(konto);
		return backend != null && this.getClass().isAssignableFrom(backend.getClass());
	}

	private class DKBScraperJobGroup extends JobGroup implements Closeable {

		protected DKBScraperJobGroup(Konto k) {
			super(k);
		}

		@Override
		public void close() throws IOException {
			// nothing to do
		}

		@Override
		protected void sync() throws Exception {
			ProgressMonitor monitor = worker.getMonitor();
			String kn = this.getKonto().getLongName();
			int step = 100 / worker.getSynchronization().size();

			try {
				this.checkInterrupted();

				monitor.log(" ");
				monitor.log(i18n.tr("Synchronisiere Konto: {0}", kn));

				Logger.info("processing jobs");
				List<DKBScraperSynchronizeJobKontoauszug> scheduledJobs = this.jobs.stream()
						.filter(DKBScraperSynchronizeJobKontoauszug.class::isInstance)
						.map(DKBScraperSynchronizeJobKontoauszug.class::cast).collect(Collectors.toList());
				if (scheduledJobs.size() != this.jobs.size()) {
					throw new IllegalArgumentException("invalid jobs scheduled");
				}

				for (DKBScraperSynchronizeJobKontoauszug job : scheduledJobs) {
					this.checkInterrupted();
					job.execute(monitor);
					monitor.addPercentComplete(step);
				}
			} catch (Exception e) {
				throw e;
			}

		}

	}

}
