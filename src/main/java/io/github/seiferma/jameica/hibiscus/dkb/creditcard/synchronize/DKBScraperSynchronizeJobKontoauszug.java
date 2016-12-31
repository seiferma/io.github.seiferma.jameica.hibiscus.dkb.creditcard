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

import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJob;
import de.willuhn.util.ProgressMonitor;

public interface DKBScraperSynchronizeJobKontoauszug extends SynchronizeJob {

	public void execute(ProgressMonitor monitor) throws Exception;
	
}
