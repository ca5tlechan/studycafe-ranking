/** 가입은 성공했는데 자동 로그인만 실패한 경우 — 재가입이 아니라 로그인으로 안내해야 한다. */
export class AutoLoginFailedError extends Error {
  constructor() {
    super('가입은 완료됐어요. 아래 로그인에서 로그인해 주세요.');
    this.name = 'AutoLoginFailedError';
  }
}
