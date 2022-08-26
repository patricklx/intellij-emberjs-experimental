import Component from "@glimmer/component";

type Args<T> = {
  inputs: T[];
  option1: string;
}

export interface GlintComponentSignature<T> {
  Args: Args<T>;
  Blocks: {
    default: [T, number]
  }
}

export default class GlintComponent<T> extends Component<GlintComponentSignature<T>> {
  args: Args<any>
}


declare module '@glint/environment-ember-loose/registry' {
  export default interface Registry {
    'example/components/glint-component': typeof GlintComponent;
    'glint-component': typeof GlintComponent;
    'example::components::glint-component': typeof GlintComponent;
    'GlintComponent': typeof GlintComponent;
  }
}
