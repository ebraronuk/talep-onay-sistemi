import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { KorumaliYol } from './KorumaliYol';
import { OturumSaglayici } from '../kimlik/OturumBaglami';
import { AMIR, oturumAc, PERSONEL } from '../test/yardimcilar';
import type { Rol } from '../api/tipler';

function gizliSayfayaGit(roller?: Rol[]) {
  return render(
    <OturumSaglayici>
      <MemoryRouter initialEntries={['/gizli']}>
        <Routes>
          <Route
            path="/gizli"
            element={
              <KorumaliYol roller={roller}>
                <p>gizli icerik</p>
              </KorumaliYol>
            }
          />
          <Route path="/giris" element={<p>giris ekrani</p>} />
          <Route path="/talepler" element={<p>talep listesi</p>} />
        </Routes>
      </MemoryRouter>
    </OturumSaglayici>,
  );
}

describe('KorumaliYol', () => {
  it('oturum yoksa giris ekranina yonlendirir', () => {
    gizliSayfayaGit();

    expect(screen.getByText('giris ekrani')).toBeInTheDocument();
    expect(screen.queryByText('gizli icerik')).not.toBeInTheDocument();
  });

  it('rol yetmiyorsa talep listesine yonlendirir', () => {
    oturumAc(PERSONEL);

    gizliSayfayaGit(['AMIR', 'YONETICI']);

    expect(screen.getByText('talep listesi')).toBeInTheDocument();
    expect(screen.queryByText('gizli icerik')).not.toBeInTheDocument();
  });

  it('rolu uyan kullaniciya icerigi gosterir', () => {
    oturumAc(AMIR);

    gizliSayfayaGit(['AMIR', 'YONETICI']);

    expect(screen.getByText('gizli icerik')).toBeInTheDocument();
  });

  it('rol siniri verilmemisse oturum acmis herkes girebilir', () => {
    oturumAc(PERSONEL);

    gizliSayfayaGit();

    expect(screen.getByText('gizli icerik')).toBeInTheDocument();
  });
});
