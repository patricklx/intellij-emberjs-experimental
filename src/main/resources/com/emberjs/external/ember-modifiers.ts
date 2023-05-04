
type OnArgs = {
  positional: [event: keyof GlobalEventHandlersEventMap, fn: Function]
}
function on(_, __, args: OnArgs): any {}

const modifiers = {
  on
}

export {
  on
}

export default modifiers;


