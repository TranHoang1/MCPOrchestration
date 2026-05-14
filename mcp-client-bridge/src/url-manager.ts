/**
 * Manages ordered URL list with rotation and error tracking.
 * Implements sequential failover per FSD UC-01 through UC-05.
 */

export interface UrlError {
  url: string;
  error: string;
  timestamp: number;
}

export class UrlManager {
  private readonly _urls: string[];
  private _urlIndex: number = 0;
  private _errors: UrlError[] = [];

  constructor(urls: string[]) {
    if (urls.length === 0) {
      throw new Error('No valid URLs configured');
    }
    this._urls = [...urls];
  }

  get activeUrl(): string { return this._urls[this._urlIndex]; }
  get urlIndex(): number { return this._urlIndex; }
  get urlCount(): number { return this._urls.length; }

  advance(): string {
    this._urlIndex = (this._urlIndex + 1) % this._urls.length;
    return this.activeUrl;
  }

  markFailed(url: string, error: string): void {
    this._errors.push({ url, error, timestamp: Date.now() });
  }

  getErrors(): UrlError[] { return [...this._errors]; }
  clearErrors(): void { this._errors = []; }
  hasNext(): boolean { return this._errors.length < this._urls.length; }

  reset(): void {
    this._urlIndex = 0;
    this._errors = [];
  }
}
