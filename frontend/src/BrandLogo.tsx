import React from 'react';

export const SAUMYA_SOLAR_LOGO_URL='https://soumya-solar-energy-solution.vercel.app/images/soumya-solar-brand.svg';

type Props={className?:string;alt?:string};
export default function BrandLogo({className='brand-logo',alt='Soumya Solar Energy Solution'}:Props){
 return <img className={className} src={SAUMYA_SOLAR_LOGO_URL} alt={alt}/>;
}
