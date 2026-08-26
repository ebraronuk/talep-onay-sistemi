import { DURUM_ETIKETLERI } from '../api/tipler';
import type { TalepDurumu } from '../api/tipler';

export function DurumRozeti({ durum }: { durum: TalepDurumu }) {
  return <span className={`rozet rozet-${durum.toLowerCase()}`}>{DURUM_ETIKETLERI[durum]}</span>;
}
