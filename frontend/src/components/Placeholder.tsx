export default function Placeholder({ title, message }: { title: string; message: string }) {
  return (
    <>
      <header className="topbar">
        <h1>{title}</h1>
      </header>
      <div className="app-body">
        <div className="center-msg">
          {message}
          <div className="soon">곧 만나요</div>
        </div>
      </div>
    </>
  );
}
