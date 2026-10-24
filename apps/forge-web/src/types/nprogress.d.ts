declare module 'nprogress' {
  const nprogress: {
    start(): void
    done(force?: boolean): void
    set(n: number): void
    configure(options: Record<string, unknown>): void
  }
  export default nprogress
}
