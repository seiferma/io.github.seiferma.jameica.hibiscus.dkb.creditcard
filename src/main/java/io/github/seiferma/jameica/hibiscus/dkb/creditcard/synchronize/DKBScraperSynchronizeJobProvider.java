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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Resource;

import de.willuhn.annotation.Lifecycle;
import de.willuhn.annotation.Lifecycle.Type;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.synchronize.SynchronizeJobProvider;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJob;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJobKontoauszug;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import io.github.seiferma.jameica.hibiscus.dkb.creditcard.account.DKBScraperAccountProperties;

@Lifecycle(Type.CONTEXT)
public class DKBScraperSynchronizeJobProvider implements SynchronizeJobProvider {

	private final static List<Class<? extends SynchronizeJob>> JOBS = createJobsList();

	@Resource
	private DKBScraperSychronizeBackend backend = null;

	@Override
	public int compareTo(Object o) {
		return 0;
	}

	@Override
	public List<SynchronizeJob> getSynchronizeJobs(Konto k) {
		List<SynchronizeJob> jobs = new LinkedList<SynchronizeJob>();
		for (Konto kt : backend.getSynchronizeKonten(k)) {
			try {
				SynchronizeJob job = createJob(kt);
				jobs.add(job);
			} catch (Exception e) {
				Logger.error("unable to load synchronize jobs", e);
			}
		}

		return jobs;
	}

	private SynchronizeJob createJob(Konto k) throws ApplicationException {
		SynchronizeJobKontoauszug job = backend.create(SynchronizeJobKontoauszug.class, k);
		job.setContext(SynchronizeJob.CTX_ENTITY, k);
		return job;
	}

	@Override
	public List<Class<? extends SynchronizeJob>> getJobTypes() {
		return JOBS;
	}

	@Override
	public boolean supports(Class<? extends SynchronizeJob> type, Konto k) {
		String ccNumber = DKBScraperAccountProperties.getCCNumber(k);
		String webUsername = DKBScraperAccountProperties.getWebUser(k);
		return ccNumber != null && webUsername != null;
	}

	private static List<Class<? extends SynchronizeJob>> createJobsList() {
		List<Class<? extends SynchronizeJob>> result = new ArrayList<>();
		result.add(DKBScraperSynchronizeJobKontoauszugImpl.class);
		return result;
	}

}
