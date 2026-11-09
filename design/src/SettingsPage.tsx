import { useEffect, useState, type FormEvent } from 'react';
import { api, type Actor } from './api';

export function SettingsPage({ actor }: { actor: Actor }) {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [loading, setLoading] = useState(true);
  const [storeBusy, setStoreBusy] = useState(false);
  const [passwordBusy, setPasswordBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    api.storeSettings().then(settings => {
      setName(settings.name); setPhone(settings.phone ?? ''); setAddress(settings.address ?? '');
    }).catch(e => setError(e instanceof Error ? e.message : 'Не удалось загрузить настройки'))
      .finally(() => setLoading(false));
  }, []);

  async function saveStore(event: FormEvent) {
    event.preventDefault(); setStoreBusy(true); setError(''); setNotice('');
    try {
      const saved = await api.updateStoreSettings(name, phone.trim() || null, address.trim() || null);
      setName(saved.name); setPhone(saved.phone ?? ''); setAddress(saved.address ?? '');
      setNotice('Данные магазина сохранены');
    } catch (e) { setError(e instanceof Error ? e.message : 'Не удалось сохранить данные магазина'); }
    finally { setStoreBusy(false); }
  }

  async function savePassword(event: FormEvent) {
    event.preventDefault(); setError(''); setNotice('');
    if (newPassword !== confirmation) { setError('Новые пароли не совпадают'); return; }
    setPasswordBusy(true);
    try {
      await api.changePassword(currentPassword, newPassword);
      setCurrentPassword(''); setNewPassword(''); setConfirmation('');
      setNotice('Пароль успешно изменён');
    } catch (e) { setError(e instanceof Error ? e.message : 'Не удалось изменить пароль'); }
    finally { setPasswordBusy(false); }
  }

  return <>
    <section className="sales-heading settings-heading"><div><p className="eyebrow">СИСТЕМА И ДОСТУП</p><h1>Настройки</h1><p>Данные магазина и безопасность вашей учётной записи.</p></div></section>
    {notice && <div className="success-banner">{notice}<button onClick={() => setNotice('')}>×</button></div>}
    {error && <div className="page-error">{error}</div>}
    <div className="settings-grid">
      <form className="panel settings-card" onSubmit={saveStore}>
        <div className="settings-card-head"><div className="settings-symbol">AZ</div><div><h2>Данные магазина</h2><p>Информация для интерфейса и будущих документов.</p></div></div>
        {loading ? <div className="empty-state">Загрузка…</div> : <div className="settings-fields">
          <label className="field">Название магазина<input value={name} onChange={e => setName(e.target.value)} maxLength={200} required disabled={actor.role !== 'OWNER'} /></label>
          <label className="field">Телефон<input value={phone} onChange={e => setPhone(e.target.value)} maxLength={40} placeholder="+7 700 000 00 00" disabled={actor.role !== 'OWNER'} /></label>
          <label className="field wide">Адрес<textarea value={address} onChange={e => setAddress(e.target.value)} maxLength={500} placeholder="Город, улица, номер магазина" disabled={actor.role !== 'OWNER'} /></label>
        </div>}
        <div className="settings-footer"><span>{actor.role === 'OWNER' ? 'Изменения сохраняются в истории действий.' : 'Изменять данные магазина может только владелец.'}</span>{actor.role === 'OWNER' && <button className="button primary" disabled={storeBusy || loading}>{storeBusy ? 'Сохраняем…' : 'Сохранить'}</button>}</div>
      </form>
      <form className="panel settings-card" onSubmit={savePassword}>
        <div className="settings-card-head"><div className="settings-symbol secure">●</div><div><h2>Смена пароля</h2><p>Вы вошли как <strong>{actor.username}</strong>. Минимум 12 символов.</p></div></div>
        <div className="settings-fields">
          <label className="field wide">Текущий пароль<input type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} maxLength={72} autoComplete="current-password" required /></label>
          <label className="field">Новый пароль<input type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} minLength={12} maxLength={72} autoComplete="new-password" required /></label>
          <label className="field">Повторите пароль<input type="password" value={confirmation} onChange={e => setConfirmation(e.target.value)} minLength={12} maxLength={72} autoComplete="new-password" required /></label>
        </div>
        <div className="settings-footer"><span>Новый пароль потребуется при следующем входе.</span><button className="button primary" disabled={passwordBusy}>{passwordBusy ? 'Изменяем…' : 'Изменить пароль'}</button></div>
      </form>
    </div>
  </>;
}
